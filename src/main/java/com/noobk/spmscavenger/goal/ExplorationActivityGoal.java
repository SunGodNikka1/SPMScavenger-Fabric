package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.DescentPressurePolicy;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.level.ServerLevel;
import com.noobk.spmscavenger.mining.NaturalDescentStatus;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MiningDirector;
import com.noobk.spmscavenger.DescentHeadingPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.goal.AnticsGoal;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.EnumSet;

/**
 * A staggered, flagless observer that distinguishes real SPM work from harmless idling. Unknown
 * running goals are deliberately treated as work, which makes this fail safely with future SPM or
 * third-party goals rather than starting an expedition over them.
 *
 * <p>This subclasses {@link RandomLookAroundGoal} solely because SPM 0.86.0's
 * {@code ObjectiveReadout#isNoise} defines that vanilla type as its public observable contract for
 * cosmetic/background goals. The observer does not call the superclass look behaviour and clears
 * its flags. Keeping it outside the visible objective stack prevents the bookkeeping label
 * "Exploration activity" from appearing beside the mob's real action.
 */
public final class ExplorationActivityGoal extends RandomLookAroundGoal {

    private static final int OBSERVE_INTERVAL = 10;

    private final PathfinderMob mob;
    private final GoalSelector selector;
    private final ExplorationReadiness readiness;

    public ExplorationActivityGoal(
            PathfinderMob mob, GoalSelector selector, ExplorationReadiness readiness) {
        super(mob);
        this.mob = mob;
        this.selector = selector;
        this.readiness = readiness;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    /** Do not inherit any cosmetic look lifecycle; only the host readout classification is used. */
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void tick() {
        if (Math.floorMod(mob.tickCount + mob.getId(), OBSERVE_INTERVAL) != 0) {
            return;
        }
        if (!ScavengerConfig.get().enabled) {
            readiness.recordMeaningfulWork();
            return;
        }

        boolean exploring = false;
        boolean meaningfulWork = false;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            if (goal instanceof ExploringGoal) {
                exploring = true;
                continue;
            }
            if (goal == this
                    || goal instanceof TrackedLocalWanderGoal
                    || goal instanceof RandomStrollGoal
                    || goal instanceof LookAtPlayerGoal
                    || goal instanceof RandomLookAroundGoal
                    || goal instanceof AnticsGoal) {
                continue;
            }
            meaningfulWork = true;
            break;
        }

        if (meaningfulWork) {
            readiness.recordMeaningfulWork();
        } else if (!exploring) {
            readiness.recordIdleTicks(OBSERVE_INTERVAL);
        }

        updateDescentPressure();
        directorTick();
    }

    /**
     * MI-14B — the director runs on this flagless observer's cadence.
     *
     * <p>It claims no goal flags and competes for nothing, so hosting it here adds no arbitration
     * surface. The decision it makes is recorded as an assigned {@link MiningProject};
     * {@code ControlledDescentGoal} picks that up on its next {@code canUse} and can no longer
     * create one itself.
     */
    private void directorTick() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        // MI-14C1: enforce the lease FIRST. Every early return below is a condition under which an
        // assignment gets stranded, so evaluating leases after them reproduces the deadlock inside
        // the director itself: combat or mobGriefing would stop the one component able to revoke.
        MiningDirector.enforceLease(level, mob, store, cfg);

        if (!cfg.enabled || !cfg.gatherResources || !cfg.exploring) {
            return;
        }
        if (mob.getTarget() != null
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        ExploringGoal exploring = ControlledDescentGoal.exploringGoalOf(mob);
        if (exploring == null) {
            return;
        }
        long now = level.getGameTime();
        NaturalDescentStatus status = exploring.naturalDescentStatus(level, now);
        if (!MiningDirector.mayStartControlledDescent(
                store, mob.getUUID(), status, readiness.hasDescentPressure())) {
            return;
        }
        DescentHeadingPolicy.Heading heading = DescentHeadingPolicy.chooseBest(
                mob.getX(),
                mob.getZ(),
                mob.blockPosition().getY(),
                (x, z) -> new int[] {
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                    ControlledDescentGoal.sampleLocalRim(level, x, z)
                },
                0,
                sector -> false,
                12,
                mob.getRandom());
        MiningDirector.startControlledDescent(level, mob, store, heading.direction());
    }

    /** MI-5 / D-MIW-031: progression diamond need above the band unlocks explore sooner. */
    private void updateDescentPressure() {
        ScavengerConfig cfg = ScavengerConfig.get();
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null || !cfg.enabled || !cfg.craftTools || !cfg.exploring) {
            readiness.clearDescentPressure();
            return;
        }
        int progression = WorkDemandPolicy.diamondProgressionDemand(
                backpack, mob.getMainHandItem(), mob.getOffhandItem(), cfg);
        boolean local = WorkDemandPolicy.isDiamondLocalGatherEligible(mob.blockPosition().getY());
        if (DescentPressurePolicy.wantsDescentExplore(progression, local, false)) {
            readiness.recordDescentPressure();
        } else {
            readiness.clearDescentPressure();
        }
    }
}
