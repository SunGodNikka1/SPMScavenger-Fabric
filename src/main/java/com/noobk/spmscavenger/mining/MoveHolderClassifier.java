package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.goal.EnvironmentalEscapeGoal;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.UUID;
import java.util.Set;

/**
 * MI-14C2/R1 — classifies running goals for arbitration and lease accounting without compiling
 * against Social Player Mobs. The executor supplies every flag it requires; MOVE-only scans are
 * insufficient because SPM's EatFoodGoal owns LOOK only.
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
        return classify(goal, mob, store, mobId, now, Set.of(Goal.Flag.MOVE));
    }

    public static MoveHolderClassification classify(
            Goal goal,
            Mob mob,
            MiningProjectSavedData store,
            UUID mobId,
            long now,
            Set<Goal.Flag> requiredFlags) {
        if (goal == null || !conflictsWithRequiredFlags(goal.getFlags(), requiredFlags)) {
            return MoveHolderClassification.NOT_MOVE_HOLDER;
        }
        if (goal instanceof EnvironmentalEscapeGoal || goal instanceof SeekShelterGoal) {
            return MoveHolderClassification.PROTECTED_SAFETY_RECOVERY;
        }
        String className = goal.getClass().getName();
        if (endsWithAny(className,
                "FireBucketGoal", "FleeFromCategoryGoal", "TrainRecoveryGoal")) {
            return MoveHolderClassification.PROTECTED_SAFETY_RECOVERY;
        }
        if (endsWithAny(className, "CommandedActionGoal", "StayNearGoal")) {
            return MoveHolderClassification.PROTECTED_PLAYER_ORDER;
        }
        if (className.endsWith("EatFoodGoal")) {
            return MoveHolderClassification.PROTECTED_LOW_FOOD;
        }
        if (endsWithAny(className,
                "SkepticalWatchGoal", "FriendlyGreetGoal", "DoorOperationGoal")) {
            return MoveHolderClassification.PROTECTED_FINITE;
        }
        if (endsWithAny(className, "FollowLovedOneGoal", "SeekAmmoGoal")) {
            return MoveHolderClassification.ORDINARY_HOST_WORK;
        }
        if (isProtectedCombatGoal(className, mob)) {
            return MoveHolderClassification.PROTECTED_COMBAT;
        }
        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, mobId, now);
        return MiningGoalKind.classify(goal, store, mobId, now)
                .map(kind -> classifyParticipant(intent, kind))
                .orElse(MoveHolderClassification.UNKNOWN_MOVE_HOLDER);
    }

    /**
     * A participating mining goal that reached here <b>is</b> holding the executor's required flags
     * — {@code conflictsWithRequiredFlags} already proved it. So the old mapping of "not YIELD
     * therefore {@code NOT_MOVE_HOLDER}" reported {@code ExecutionBlocker.NONE} for a goal
     * demonstrably in possession of {@code MOVE}, telling the lease that execution was available
     * while something else was driving the mob.
     *
     * <p>Three cases, not two:
     *
     * <ul>
     *   <li><b>YIELD</b> — an ordinary chore competing with mining: contention.</li>
     *   <li><b>ALLOW, designated consumer</b> — the project's own executor holding its own flags:
     *       genuinely nothing in the way.</li>
     *   <li><b>ALLOW, not the designated consumer</b> — a downstream consumer doing work the
     *       project wants done: Cooperative Resource Handoff, which pauses rather than ages the
     *       lease.</li>
     * </ul>
     */
    private static MoveHolderClassification classifyParticipant(
            ExecutionIntent intent, MiningGoalKind kind) {
        ArbitrationDecision decision = MiningExecutionArbiter.decide(intent, kind);
        if (decision == ArbitrationDecision.YIELD) {
            return MoveHolderClassification.PARTICIPATING_YIELD;
        }
        if (decision == ArbitrationDecision.ALLOW && !kind.isDesignatedConsumer()) {
            return MoveHolderClassification.COOPERATIVE_PROJECT_WORK;
        }
        return MoveHolderClassification.NOT_MOVE_HOLDER;
    }

    public static boolean conflictsWithRequiredFlags(
            Set<Goal.Flag> holderFlags, Set<Goal.Flag> requiredFlags) {
        if (holderFlags == null || requiredFlags == null
                || holderFlags.isEmpty() || requiredFlags.isEmpty()) {
            return false;
        }
        for (Goal.Flag flag : requiredFlags) {
            if (holderFlags.contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /** Lease impact is deliberately separate from whether mining may force the holder to yield. */
    public static ExecutionBlocker leaseBlocker(MoveHolderClassification classification) {
        return switch (classification) {
            case PARTICIPATING_YIELD, ORDINARY_HOST_WORK, UNKNOWN_MOVE_HOLDER ->
                    ExecutionBlocker.CONTENTION;
            case PROTECTED_SAFETY_RECOVERY -> ExecutionBlocker.SAFETY_RECOVERY;
            case PROTECTED_PLAYER_ORDER -> ExecutionBlocker.PLAYER_ORDER;
            case PROTECTED_COMBAT -> ExecutionBlocker.COMBAT_TARGET;
            case PROTECTED_LOW_FOOD -> ExecutionBlocker.LOW_FOOD;
            case PROTECTED_FINITE -> ExecutionBlocker.HOST_INTERRUPT;
            case COOPERATIVE_PROJECT_WORK -> ExecutionBlocker.COOPERATIVE_WORK;
            case NOT_MOVE_HOLDER -> ExecutionBlocker.NONE;
        };
    }

    private static boolean endsWithAny(String className, String... suffixes) {
        for (String suffix : suffixes) {
            if (className.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtectedCombatGoal(String className, @org.jetbrains.annotations.Nullable Mob mob) {
        if (mob != null
                && mob.getTarget() != null
                && (className.contains("MeleeAttackGoal")
                        || className.contains("RangedAttackGoal")
                        || className.contains("BowAttackGoal"))) {
            return true;
        }
        return className.endsWith("PlayerMobAttackGoal")
                || className.endsWith("TntCombatGoal")
                || className.endsWith("EndCrystalCombatGoal")
                || className.endsWith("WeaponAwareAttackGoal")
                || className.endsWith("PlayerMobBowAttackGoal")
                || className.endsWith("PlayerMobCrossbowAttackGoal")
                || className.endsWith("ModdedRangedAttackGoal");
    }

    public static boolean blocksMiningExecution(
            ExecutionIntent intent, MoveHolderClassification classification) {
        if (!intent.isActionable()) {
            return false;
        }
        return switch (classification) {
            case PARTICIPATING_YIELD, ORDINARY_HOST_WORK, UNKNOWN_MOVE_HOLDER -> true;
            case PROTECTED_SAFETY_RECOVERY, PROTECTED_PLAYER_ORDER, PROTECTED_COMBAT,
                    PROTECTED_LOW_FOOD, PROTECTED_FINITE, COOPERATIVE_PROJECT_WORK,
                    NOT_MOVE_HOLDER -> false;
        };
    }
}
