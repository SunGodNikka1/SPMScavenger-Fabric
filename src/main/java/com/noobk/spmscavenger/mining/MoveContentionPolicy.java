package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * MI-14C2 — detects when an actionable mining intent exists but a chore that should yield still
 * holds {@code MOVE} in the physical scheduler.
 */
public final class MoveContentionPolicy {

    private MoveContentionPolicy() {
    }

    /**
     * @param excluded running goal to ignore, usually the executor asking for admission
     */
    public static boolean hasYieldingMoveHolder(
            Mob mob,
            @Nullable Goal excluded,
            MiningProjectSavedData store,
            long now) {
        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, mob.getUUID(), now);
        if (!intent.isActionable()) {
            return false;
        }
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null) {
            return false;
        }
        UUID mobId = mob.getUUID();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            if (goal == excluded) {
                continue;
            }
            MiningGoalKind kind =
                    MiningGoalKind.classify(goal, store, mobId, now).orElse(null);
            if (kind == null) {
                continue;
            }
            if (MiningExecutionArbiter.decide(intent, kind) == ArbitrationDecision.YIELD) {
                return true;
            }
        }
        return false;
    }
}
