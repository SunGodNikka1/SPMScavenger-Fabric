package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.goal.EnvironmentalEscapeGoal;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.UUID;

/**
 * MI-14C2-R2 — classifies running goals for scheduler-wide MOVE contention without compiling
 * against Social Player Mobs.
 */
public final class MoveHolderClassifier {

    private MoveHolderClassifier() {
    }

    public static MoveHolderClassification classify(
            Goal goal,
            Mob mob,
            MiningProjectSavedData store,
            UUID mobId,
            long now) {
        if (goal == null || !goal.getFlags().contains(Goal.Flag.MOVE)) {
            return MoveHolderClassification.NOT_MOVE_HOLDER;
        }
        if (goal instanceof EnvironmentalEscapeGoal || goal instanceof SeekShelterGoal) {
            return MoveHolderClassification.PROTECTED_INTERRUPT;
        }
        String className = goal.getClass().getName();
        if (className.endsWith("TrainRecoveryGoal") || className.endsWith("StayNearGoal")) {
            return MoveHolderClassification.PROTECTED_INTERRUPT;
        }
        if (className.endsWith("FollowLovedOneGoal")) {
            return MoveHolderClassification.ORDINARY_HOST_WORK;
        }
        if (isProtectedCombatGoal(className, mob)) {
            return MoveHolderClassification.PROTECTED_INTERRUPT;
        }
        return MiningGoalKind.classify(goal, store, mobId, now)
                .map(kind -> MiningExecutionArbiter.decide(
                                ExecutionIntentPolicy.derive(store, mobId, now), kind)
                        == ArbitrationDecision.YIELD
                        ? MoveHolderClassification.PARTICIPATING_YIELD
                        : MoveHolderClassification.NOT_MOVE_HOLDER)
                .orElse(MoveHolderClassification.UNKNOWN_MOVE_HOLDER);
    }

    private static boolean isProtectedCombatGoal(String className, @org.jetbrains.annotations.Nullable Mob mob) {
        if (mob != null
                && mob.getTarget() != null
                && (className.contains("MeleeAttackGoal")
                        || className.contains("RangedAttackGoal")
                        || className.contains("BowAttackGoal"))) {
            return true;
        }
        return className.endsWith("PlayerMobAttackGoal");
    }

    public static boolean blocksMiningExecution(
            ExecutionIntent intent, MoveHolderClassification classification) {
        if (!intent.isActionable()) {
            return false;
        }
        return switch (classification) {
            case PARTICIPATING_YIELD, ORDINARY_HOST_WORK, UNKNOWN_MOVE_HOLDER -> true;
            case PROTECTED_INTERRUPT, NOT_MOVE_HOLDER -> false;
        };
    }
}
