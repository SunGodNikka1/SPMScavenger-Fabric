package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * MI-14C3-R1 — maps physical scheduler unavailability to lease meaning.
 *
 * <p>This is deliberately not an arbitration policy. A protected holder is never forced to yield,
 * but it still produces a blocker because the executor cannot acquire its complete flag set.
 */
public final class SchedulerConflictPolicy {

    private SchedulerConflictPolicy() {
    }

    public static ExecutionBlocker resolveBlocker(
            Mob mob,
            @Nullable Goal excluded,
            MiningProjectSavedData store,
            long now,
            Set<Goal.Flag> requiredFlags) {
        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, mob.getUUID(), now);
        return resolveBlocker(mob, excluded, store, now, requiredFlags, intent);
    }

    /** Admission overload: evaluates the authority the director is considering before it exists. */
    public static ExecutionBlocker resolveBlocker(
            Mob mob,
            @Nullable Goal excluded,
            MiningProjectSavedData store,
            long now,
            Set<Goal.Flag> requiredFlags,
            ExecutionIntent intent) {
        if (!intent.isActionable()) {
            return ExecutionBlocker.NONE;
        }
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null) {
            return ExecutionBlocker.NONE;
        }
        UUID mobId = mob.getUUID();
        ExecutionBlocker strongest = ExecutionBlocker.NONE;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            if (goal == excluded) {
                continue;
            }
            MoveHolderClassification classification = MoveHolderClassifier.classify(
                    goal, mob, store, mobId, now, requiredFlags);
            ExecutionBlocker candidate = MoveHolderClassifier.leaseBlocker(classification);
            if (rank(candidate) > rank(strongest)) {
                strongest = candidate;
                if (strongest == ExecutionBlocker.PLAYER_ORDER) {
                    return strongest;
                }
            }
        }
        return strongest;
    }

    /** Only explicit player authority prevents creating an autonomous assignment outright. */
    public static boolean preventsAssignment(ExecutionBlocker blocker) {
        return blocker == ExecutionBlocker.PLAYER_ORDER;
    }

    private static int rank(ExecutionBlocker blocker) {
        return switch (blocker) {
            case PLAYER_ORDER -> 5;
            case COMBAT_TARGET -> 4;
            case SAFETY_RECOVERY -> 3;
            case LOW_FOOD, HOST_INTERRUPT -> 2;
            case CONTENTION -> 1;
            default -> 0;
        };
    }
}
