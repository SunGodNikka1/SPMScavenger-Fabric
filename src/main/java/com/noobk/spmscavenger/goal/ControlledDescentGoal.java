package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.DescentHeadingPolicy;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.ToolBox;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.mining.ControlledDescentCaveHandoff;
import com.noobk.spmscavenger.mining.MiningProject;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MiningTransition;
import com.noobk.spmscavenger.mining.NaturalDescentExhaustionPolicy;
import com.noobk.spmscavenger.mining.NaturalDescentStatus;
import com.noobk.spmscavenger.mining.StairStepPlan;
import com.noobk.spmscavenger.mining.StairStepPlanner;
import com.noobk.spmscavenger.mining.StairStepSafety;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.UUID;

/**
 * MI-7E — bounded controlled staircase descent when natural descent is {@link NaturalDescentStatus#EXHAUSTED}.
 */
public final class ControlledDescentGoal extends Goal {

    private static final int MAX_BREAK_TICKS = 200;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25;

    private final PathfinderMob mob;
    private final double speed;
    private final ExplorationReadiness readiness;

    private MiningProject project;
    private StairStepPlan currentStep;
    private BlockPos breakTarget;
    private int breakIndex;
    private int breakTicks;
    private int breakTotal;

    public ControlledDescentGoal(PathfinderMob mob, double speed, ExplorationReadiness readiness) {
        this.mob = mob;
        this.speed = speed;
        this.readiness = readiness;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.gatherResources || !cfg.exploring) {
            return false;
        }
        if (mob.getTarget() != null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        if (!hasUsablePick()) {
            return false;
        }
        MiningProject active = activeProject(level);
        if (active != null && active.isControlledDescent()) {
            return true;
        }
        // MI-14A: an unconsumed SEARCH_BUDGET_EXHAUSTED or HANDOFF_TUNNEL_SEARCH is a claim on the
        // next decision. Without it the loop is: exhaust budget -> project deleted -> descent
        // pressure unchanged -> exhausted again -> fresh descent in the same area, indefinitely.
        // Checked after the resume branch so an already-running project is never interrupted.
        if (MiningProjectSavedData.get(level).pendingTransition(mob.getUUID())
                .filter(MiningTransition::blocksControlledDescentRestart)
                .isPresent()) {
            return false;
        }
        if (!readiness.hasDescentPressure()) {
            return false;
        }
        ExploringGoal exploring = exploringGoalOf(mob);
        if (exploring == null) {
            return false;
        }
        NaturalDescentStatus status = exploring.naturalDescentStatus(level, level.getGameTime());
        return NaturalDescentExhaustionPolicy.mayStartControlledDescent(status);
    }

    @Override
    public boolean canContinueToUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        return project != null
                && project.isControlledDescent()
                && mob.getTarget() == null
                && cfg.enabled
                && cfg.gatherResources
                && mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @Override
    public void start() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        UUID mobId = mob.getUUID();
        project = store.projectOf(mobId).filter(MiningProject::isControlledDescent).orElse(null);
        if (project == null) {
            DescentHeadingPolicy.Heading heading = DescentHeadingPolicy.chooseBest(
                    mob.getX(),
                    mob.getZ(),
                    mob.blockPosition().getY(),
                    (x, z) -> new int[] {
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                        sampleLocalRim(level, x, z)
                    },
                    0,
                    sector -> false,
                    12,
                    mob.getRandom());
            project = MiningProject.startControlledDescent(
                    mob.blockPosition(), heading.direction(), level.getGameTime());
            store.putProject(mobId, project);
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] controlled descent started entity={} heading={} origin={}",
                    mob.getId(), heading.direction(), mob.blockPosition());
        }
        planNextStep(level);
    }

    @Override
    public void stop() {
        if (project != null && mob.level() instanceof ServerLevel level) {
            MiningProjectSavedData.get(level).putProject(mob.getUUID(), project);
        }
        currentStep = null;
        breakTarget = null;
        breakIndex = 0;
        breakTicks = 0;
        breakTotal = 0;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || project == null) {
            return;
        }
        project = project.withBudgetUsage(project.budgetUsage().withTick());
        if (project.isBudgetExhausted()) {
            finish(level, MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED);
            return;
        }
        if (shouldHandoffTunnelSearch(level)) {
            finish(level, MiningProjectEnd.HANDOFF_TUNNEL_SEARCH);
            return;
        }
        if (currentStep == null) {
            planNextStep(level);
            return;
        }
        if (breakTarget != null) {
            tickBreak(level);
            return;
        }
        if (!mob.blockPosition().closerThan(currentStep.nextStandCell(), 1.5)) {
            mob.getNavigation().moveTo(
                    currentStep.nextStandCell().getX() + 0.5,
                    currentStep.nextStandCell().getY(),
                    currentStep.nextStandCell().getZ() + 0.5,
                    speed);
            return;
        }
        completeStep(level);
    }

    private void tickBreak(ServerLevel level) {
        BlockState state = level.getBlockState(breakTarget);
        if (state.isAir()) {
            advanceBreak(level);
            return;
        }
        mob.getLookControl().setLookAt(
                breakTarget.getX() + 0.5, breakTarget.getY() + 0.5, breakTarget.getZ() + 0.5);
        if (breakTotal == 0) {
            ToolBox.equipFor(mob, state);
            breakTotal = breakTicksFor(state);
        }
        if (++breakTicks % 5 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (breakTicks >= breakTotal) {
            level.destroyBlock(breakTarget, true, mob);
            project = project.withBudgetUsage(project.budgetUsage().withBlocksMined(1));
            advanceBreak(level);
        }
    }

    private void advanceBreak(ServerLevel level) {
        breakTarget = null;
        breakTicks = 0;
        breakTotal = 0;
        breakIndex++;
        if (currentStep != null && breakIndex < currentStep.requiredBreaks().size()) {
            beginBreak(level, currentStep.requiredBreaks().get(breakIndex));
            return;
        }
        mob.getNavigation().moveTo(
                currentStep.nextStandCell().getX() + 0.5,
                currentStep.nextStandCell().getY(),
                currentStep.nextStandCell().getZ() + 0.5,
                speed);
    }

    private void beginBreak(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            breakIndex++;
            if (currentStep != null && breakIndex < currentStep.requiredBreaks().size()) {
                beginBreak(level, currentStep.requiredBreaks().get(breakIndex));
            }
            return;
        }
        StairStepSafety.Rejection rejection = StairStepSafety.validateBreakHazards(level, pos);
        if (rejection != StairStepSafety.Rejection.NONE) {
            finish(level, MiningProjectEnd.HAZARD);
            return;
        }
        if (!ToolBox.ownsToolFor(mob, state)) {
            finish(level, MiningProjectEnd.NO_PROGRESS);
            return;
        }
        breakTarget = pos.immutable();
        breakTicks = 0;
        breakTotal = 0;
        mob.getNavigation().stop();
    }

    private void planNextStep(ServerLevel level) {
        currentStep = null;
        breakTarget = null;
        breakIndex = 0;
        BlockPos stand = mob.blockPosition();
        StairStepPlan plan = StairStepPlanner.planStep(stand, project.heading());
        StairStepSafety.Rejection rejection = StairStepSafety.validatePlan(level, plan, mob);
        if (rejection != StairStepSafety.Rejection.NONE) {
            project = project.withBudgetUsage(project.budgetUsage().withFailedStep());
            if (project.budget().isFailuresExhausted(project.budgetUsage())) {
                finish(level, MiningProjectEnd.NO_PROGRESS);
            }
            return;
        }
        currentStep = plan;
        breakIndex = 0;
        beginBreak(level, plan.requiredBreaks().get(0));
    }

    private void completeStep(ServerLevel level) {
        BlockPos previous = mob.blockPosition();
        project = project
                .withLastSafeAnchor(previous)
                .withDepthBelowOrigin(project.origin().getY() - mob.blockPosition().getY())
                .pushReturnStep(previous)
                .withBudgetUsage(project.budgetUsage().withProgress(
                        horizontalDistance(project.origin(), mob.blockPosition()),
                        project.origin().getY() - mob.blockPosition().getY()));
        persist(level);
        if (ControlledDescentCaveHandoff.openedTraversableCave(
                level, mob.blockPosition(), project.heading(), this::canStand)) {
            finish(level, MiningProjectEnd.CAVE_FOUND);
            return;
        }
        if (shouldHandoffTunnelSearch(level)) {
            finish(level, MiningProjectEnd.HANDOFF_TUNNEL_SEARCH);
            return;
        }
        planNextStep(level);
    }

    private boolean shouldHandoffTunnelSearch(ServerLevel level) {
        int feetY = mob.blockPosition().getY();
        if (!WorkDemandPolicy.isDiamondLocalGatherEligible(feetY)) {
            return false;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        return WorkDemandPolicy.diamondProgressionDemand(
                PlayerMobs.backpack(mob),
                mob.getMainHandItem(),
                mob.getOffhandItem(),
                cfg) > 0;
    }

    private boolean canStand(BlockPos position) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!level.isPositionEntityTicking(position)) {
            return false;
        }
        if (!level.getFluidState(position).isEmpty()
                || !level.getFluidState(position.below()).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                || !level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()) {
            return false;
        }
        BlockPos below = position.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private void finish(ServerLevel level, MiningProjectEnd end) {
        // MI-14A: record the outcome *with* the data a consumer needs, atomically with completion.
        // completeProject deletes terminal projects, so a reason emitted without a payload here is
        // gone before anything can read it.
        MiningTransition transition = MiningTransition.of(
                project, end, mob.blockPosition(), level.getGameTime());
        SpmScavenger.LOGGER.info(
                "[spmscavenger] controlled descent ended entity={} reason={} depth={} at={} heading={}",
                mob.getId(), end, project.depthBelowOrigin(), transition.at(), transition.heading());
        MiningProjectSavedData.get(level).completeProject(mob.getUUID(), end, transition);
        project = null;
        currentStep = null;
        stop();
    }

    private void persist(ServerLevel level) {
        MiningProjectSavedData.get(level).putProject(mob.getUUID(), project);
    }

    private static int horizontalDistance(BlockPos origin, BlockPos current) {
        int dx = current.getX() - origin.getX();
        int dz = current.getZ() - origin.getZ();
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static int sampleLocalRim(ServerLevel level, int originX, int originZ) {
        int[] ox = com.noobk.spmscavenger.CaveContextPolicy.rimSampleOffsetsX();
        int[] oz = com.noobk.spmscavenger.CaveContextPolicy.rimSampleOffsetsZ();
        int[] samples = new int[ox.length];
        for (int i = 0; i < ox.length; i++) {
            samples[i] = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, originX + ox[i], originZ + oz[i]);
        }
        return com.noobk.spmscavenger.CaveContextPolicy.localRimHeight(samples);
    }

    private MiningProject activeProject(ServerLevel level) {
        return MiningProjectSavedData.get(level)
                .projectOf(mob.getUUID())
                .filter(MiningProject::isControlledDescent)
                .orElse(null);
    }

    private boolean hasUsablePick() {
        return ToolTierPolicy.tierOfPick(
                PlayerMobs.backpack(mob),
                mob.getMainHandItem(),
                mob.getOffhandItem()) != ToolTier.NONE;
    }

    private int breakTicksFor(BlockState state) {
        float speed = state.getDestroySpeed(mob.level(), breakTarget);
        if (speed <= 0.0f) {
            return MAX_BREAK_TICKS;
        }
        ItemStack tool = mob.getMainHandItem();
        float multiplier = tool.isEmpty() ? 1.0f : tool.getDestroySpeed(state);
        int ticks = (int) Math.ceil(1.0f / (speed * Math.max(0.1f, multiplier)) * 20.0f);
        return Math.max(1, Math.min(MAX_BREAK_TICKS, ticks));
    }

    private static ExploringGoal exploringGoalOf(Mob other) {
        GoalSelector selector = ((MobGoalSelectorAccessor) other).spmscavenger$getGoalSelector();
        if (selector == null) {
            return null;
        }
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ExploringGoal goal) {
                return goal;
            }
        }
        return null;
    }
}
