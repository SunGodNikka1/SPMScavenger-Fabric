package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ToolBox;
import com.noobk.spmscavenger.mining.CaveOpening;
import com.noobk.spmscavenger.mining.ControlledDescentCaveHandoff;
import com.noobk.spmscavenger.mining.ExposureOpportunityPolicy;
import com.noobk.spmscavenger.mining.HorizontalStepPlanner;
import com.noobk.spmscavenger.mining.HorizontalStepSafety;
import com.noobk.spmscavenger.mining.MiningDirector;
import com.noobk.spmscavenger.mining.MiningExecutionGuard;
import com.noobk.spmscavenger.mining.MiningGoalKind;
import com.noobk.spmscavenger.mining.MiningProject;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.StairStepPlan;
import com.noobk.spmscavenger.mining.StairStepSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * D-MIW-TS1 — bounded 1x2 horizontal corridor at the diamond band.
 *
 * <h2>What this executor is for</h2>
 *
 * It <b>creates exposure</b>. It never asks where ore is: ore inside rock is {@code UNDISCOVERED}
 * by the mod's own perception contract, and a scan would both duplicate
 * {@code GatherResourcesGoal} and read through solid stone. After cutting a cell it knows only
 * which cells it opened, offers that boundary, and steps aside so the existing gather loop can
 * consume whatever the cut revealed.
 *
 * <p>Success is therefore <em>ore exposed and gathered</em>, never <em>ore located</em> — which is
 * why this class contains no target selection of any kind.
 */
public final class TunnelSearchGoal extends Goal {

    private static final int MAX_BREAK_TICKS = 200;
    private static final double ARRIVAL_DISTANCE = 1.5;

    private final PathfinderMob mob;
    private final double speed;

    private MiningProject project;
    private StairStepPlan currentStep;
    private BlockPos breakTarget;
    private int breakIndex;
    private int breakTicks;
    private int breakTotal;

    public TunnelSearchGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        Optional<MiningProject> assigned = MiningDirector.assignedProject(
                store, mob.getUUID(), MiningProjectMode.TUNNEL_SEARCH);
        if (assigned.isEmpty()) {
            return false;
        }
        // M2 - stand aside for any live exposure, not only one already being worked.
        //
        // Yielding solely on ACQUIRING was circular: both goals sit at priority 3, so gather cannot
        // preempt, so it can never run the probe, so ACQUIRING is never reached, so the tunnel
        // never yields. An OFFERED exposure must release the flags for the probe to happen at all.
        // If nothing wants it, the offer expires on its own clock and the tunnel resumes.
        if (ExposureOpportunityPolicy.isLive(
                store.exposureOf(mob.getUUID()).orElse(null), assigned.get(),
                level.getGameTime())) {
            return false;
        }
        if (!MiningExecutionGuard.permits(mob, this, MiningGoalKind.TUNNEL_SEARCH)) {
            return false;
        }
        return MiningDirector.authorizeExecution(
                level, mob, store, assigned.get(),
                MiningDirector.resolveMiningExecutionBlocker(
                        level, mob, ScavengerConfig.get(), store, this));
    }

    @Override
    public boolean canContinueToUse() {
        if (project == null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (project.mode() != MiningProjectMode.TUNNEL_SEARCH || !project.isActive()) {
            return false;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        // M2 - release the flags the moment this project has an exposure worth consuming.
        if (ExposureOpportunityPolicy.isLive(
                store.exposureOf(mob.getUUID()).orElse(null), project, level.getGameTime())) {
            return false;
        }
        // M4 - re-check authority every tick, exactly as the descent executor does. Checking only
        // the raw blocker let a revoked project keep running from this goal's stale local copy: the
        // director removes the stored project and clears the lease, and the executor never notices.
        MiningProject assigned = MiningDirector.assignedProject(
                        store, mob.getUUID(), MiningProjectMode.TUNNEL_SEARCH)
                .orElse(null);
        if (assigned == null || !assigned.matchesSession(project)) {
            return false;
        }
        if (!MiningExecutionGuard.permits(mob, this, MiningGoalKind.TUNNEL_SEARCH)) {
            return false;
        }
        return MiningDirector.authorizeExecution(
                level, mob, store, assigned,
                MiningDirector.resolveMiningExecutionBlocker(
                        level, mob, ScavengerConfig.get(), store, this));
    }

    @Override
    public void start() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        project = MiningDirector.assignedProject(
                        MiningProjectSavedData.get(level), mob.getUUID(),
                        MiningProjectMode.TUNNEL_SEARCH)
                .orElse(null);
        if (project == null) {
            return;
        }
        MiningDirector.markExecutorStarted(level, mob);
        planNextStep(level);
    }

    @Override
    public void stop() {
        // M4 - never write a local copy back unconditionally. If the director revoked this project
        // while the goal held it, an unguarded checkpoint resurrects a project the control plane
        // deliberately destroyed - the zombie C2-R1 already removed for controlled descent.
        if (project != null && mob.level() instanceof ServerLevel level) {
            MiningProjectSavedData store = MiningProjectSavedData.get(level);
            if (MiningDirector.shouldPersistExecutorCheckpoint(store, mob.getUUID(), project)) {
                store.putProject(mob.getUUID(), project);
            }
        }
        project = null;
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
        if (currentStep == null) {
            planNextStep(level);
            return;
        }
        if (breakTarget != null) {
            tickBreak(level);
            return;
        }
        if (!mob.blockPosition().closerThan(currentStep.nextStandCell(), ARRIVAL_DISTANCE)) {
            moveToStand();
            return;
        }
        completeStep(level);
    }

    private void planNextStep(ServerLevel level) {
        BlockPos stand = mob.blockPosition();
        StairStepPlan plan = HorizontalStepPlanner.planStep(stand, project.heading());
        StairStepSafety.Rejection rejection = HorizontalStepSafety.validatePlan(
                level, plan, StairStepSafety.BreakCapability.fromMob(mob));
        if (rejection != StairStepSafety.Rejection.NONE) {
            finish(level, rejection == StairStepSafety.Rejection.NO_HARVEST
                    ? MiningProjectEnd.TOOL_FAILURE
                    : MiningProjectEnd.HAZARD);
            return;
        }
        currentStep = plan;
        breakIndex = 0;
        beginBreak(level, plan.requiredBreaks().get(0));
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
            finish(level, MiningProjectEnd.TOOL_FAILURE);
            return;
        }
        breakTarget = pos.immutable();
        breakTicks = 0;
        breakTotal = 0;
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
            if (!level.destroyBlock(breakTarget, true, mob)) {
                // Denied break: not progress, so the C3 watchdog can still tell a stuck executor
                // from one that is merely ticking.
                return;
            }
            project = project.withBudgetUsage(project.budgetUsage().withBlocksMined(1));
            MiningDirector.markExecutionProgress(level, mob);
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
        moveToStand();
    }

    private void moveToStand() {
        mob.getNavigation().moveTo(
                currentStep.nextStandCell().getX() + 0.5,
                currentStep.nextStandCell().getY(),
                currentStep.nextStandCell().getZ() + 0.5,
                speed);
    }

    /**
     * The cell is cut. Offer what it opened, then check whether it opened into a cave.
     *
     * <p>Order matters: a breakthrough ends the project, and ending it clears the exposure, so
     * offering afterwards would publish a boundary belonging to a project that no longer exists.
     */
    private void completeStep(ServerLevel level) {
        StairStepPlan completed = currentStep;
        project = project.pushReturnStep(mob.blockPosition())
                .withLastSafeAnchor(mob.blockPosition());
        persist(level);

        if (completed != null) {
            List<BlockPos> opened = completed.requiredBreaks();
            MiningProjectSavedData.get(level)
                    .offerExposure(mob.getUUID(), project, opened, level.getGameTime());
        }

        // D-MIW-TS4: a corridor that opens into a real cave hands off rather than tunnelling
        // through it. Evidence rules are the R2 family's, unchanged - connected through air from
        // cells actually excavated, never perceived through a wall.
        if (completed != null) {
            Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                    level, completed, project.heading(), this::canPass, this::canStand);
            if (opening.isPresent()) {
                MiningDirector.markExecutionProgress(level, mob);
                MiningDirector.completeWithOpening(
                        level, mob, project, opening.get(), mob.blockPosition());
                project = null;
                currentStep = null;
                stop();
                return;
            }
        }
        currentStep = null;
    }

    private boolean canPass(BlockPos position) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!level.isPositionEntityTicking(position)) {
            return false;
        }
        if (!level.getFluidState(position).isEmpty()) {
            return false;
        }
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    private boolean canStand(BlockPos position) {
        if (!canPass(position) || !canPass(position.above())) {
            return false;
        }
        ServerLevel level = (ServerLevel) mob.level();
        if (!level.getFluidState(position.below()).isEmpty()) {
            return false;
        }
        return level.getBlockState(position.below())
                .isFaceSturdy(level, position.below(), net.minecraft.core.Direction.UP);
    }

    private int breakTicksFor(BlockState state) {
        float speedOf = state.getDestroySpeed(mob.level(), breakTarget);
        if (speedOf <= 0.0f) {
            return MAX_BREAK_TICKS;
        }
        float multiplier = mob.getMainHandItem().isEmpty()
                ? 1.0f
                : mob.getMainHandItem().getDestroySpeed(state);
        int ticks = (int) Math.ceil(1.0f / (speedOf * Math.max(0.1f, multiplier)) * 20.0f);
        return Math.max(1, Math.min(MAX_BREAK_TICKS, ticks));
    }

    private void persist(ServerLevel level) {
        MiningProjectSavedData.get(level).putProject(mob.getUUID(), project);
    }

    private void finish(ServerLevel level, MiningProjectEnd end) {
        MiningDirector.markExecutionProgress(level, mob);
        MiningDirector.completeProject(level, mob, project, end, mob.blockPosition());
        project = null;
        currentStep = null;
        stop();
    }
}
