package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.goal.AnticsGoal;
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
    }
}
