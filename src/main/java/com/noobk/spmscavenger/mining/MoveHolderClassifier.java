package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.AnticsGoal;
import com.noobk.spmscavenger.goal.CampfireGoal;
import com.noobk.spmscavenger.goal.ControlledDescentGoal;
import com.noobk.spmscavenger.goal.CraftTorchesGoal;
import com.noobk.spmscavenger.goal.EnvironmentalEscapeGoal;
import com.noobk.spmscavenger.goal.ExplorationActivityGoal;
import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.goal.GatherResourcesGoal;
import com.noobk.spmscavenger.goal.PlaceTorchGoal;
import com.noobk.spmscavenger.goal.SeekShelterGoal;
import com.noobk.spmscavenger.goal.SmeltAtFurnaceGoal;
import com.noobk.spmscavenger.goal.TradeWithVillagerGoal;
import com.noobk.spmscavenger.goal.TrackedLocalWanderGoal;
import com.noobk.spmscavenger.goal.TunnelSearchGoal;
import com.noobk.spmscavenger.goal.VillagePerceptionObserver;
import com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * MI-14C2/R1 — classifies running goals for arbitration and lease accounting without compiling
 * against Social Player Mobs. The executor supplies every flag it requires; MOVE-only scans are
 * insufficient because SPM's EatFoodGoal owns LOOK only.
 */
public final class MoveHolderClassifier {

    private MoveHolderClassifier() {
    }

    /**
     * GAO-0 semantic taxonomy. This method is the shared source for SPM suffix knowledge; activity
     * observers must not reproduce these strings in another classifier.
     */
    public static ActivityClass activityClass(
            Goal goal,
            @Nullable Mob mob,
            @Nullable MiningProjectSavedData store,
            UUID mobId,
            long now) {
        if (goal == null) {
            return ActivityClass.UNKNOWN_ACTIVE;
        }
        ActivityClass base = staticActivityClass(goal.getClass());
        if (goal instanceof SeekShelterGoal shelterGoal) {
            return shelterGoal.isRestingAtShelter()
                    ? ActivityClass.SHELTER_HOLD
                    : base;
        }
        if (goal instanceof ExploringGoal) {
            if (store != null
                    && MiningGoalKind.classifyExploring(store, mobId, now)
                            == MiningGoalKind.EXPLORING_CAVE_HANDOFF) {
                return ActivityClass.PROJECT_EXECUTION;
            }
            return ActivityClass.EXPEDITION;
        }
        if (goal instanceof GatherResourcesGoal
                || goal instanceof CraftTorchesGoal
                || goal instanceof SmeltAtFurnaceGoal) {
            if (store != null) {
                MiningGoalKind kind = MiningGoalKind.classify(goal, store, mobId, now)
                        .orElseThrow();
                ExecutionIntent intent = ExecutionIntentPolicy.derive(store, mobId, now);
                if (MiningExecutionArbiter.decide(intent, kind) == ArbitrationDecision.ALLOW
                        && !kind.isDesignatedConsumer()) {
                    return ActivityClass.PRODUCTIVE_COOP;
                }
            }
            return ActivityClass.SCAVENGE_WORK;
        }
        if (base == ActivityClass.SOCIAL_REFLEX
                && goal.getClass().getName().endsWith("FriendlyGreetGoal")) {
            return SocialExecutionBindingRegistry.friendlyGreetActivityClass(mobId);
        }
        return base;
    }

    /** Static portion of the GAO-0 taxonomy; no Goal instance or Minecraft world is required. */
    public static ActivityClass staticActivityClass(Class<? extends Goal> goalType) {
        if (EnvironmentalEscapeGoal.class.isAssignableFrom(goalType)
                || SeekShelterGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.MANDATORY_SAFETY;
        }
        if (ControlledDescentGoal.class.isAssignableFrom(goalType)
                || TunnelSearchGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.PROJECT_EXECUTION;
        }
        if (PlaceTorchGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.MAINTENANCE;
        }
        if (CampfireGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.REST_APPROACH;
        }
        if (TrackedLocalWanderGoal.class.isAssignableFrom(goalType)
                || RandomStrollGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.IDLE_CANDIDATE;
        }
        if (ExplorationActivityGoal.class.isAssignableFrom(goalType)
                || VillagePerceptionObserver.class.isAssignableFrom(goalType)) {
            return ActivityClass.PASSIVE_OBSERVER;
        }
        if (AnticsGoal.class.isAssignableFrom(goalType)
                || LookAtPlayerGoal.class.isAssignableFrom(goalType)
                || RandomLookAroundGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.PASSIVE_COSMETIC;
        }
        if (ExploringGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.EXPEDITION;
        }
        if (TradeWithVillagerGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.VILLAGE_TRADE;
        }
        if (GatherResourcesGoal.class.isAssignableFrom(goalType)
                || CraftTorchesGoal.class.isAssignableFrom(goalType)
                || SmeltAtFurnaceGoal.class.isAssignableFrom(goalType)) {
            return ActivityClass.SCAVENGE_WORK;
        }

        String className = goalType.getName();
        if (endsWithAny(className,
                "FireBucketGoal", "FleeFromCategoryGoal", "TrainRecoveryGoal")) {
            return ActivityClass.MANDATORY_SAFETY;
        }
        if (endsWithAny(className, "CommandedActionGoal", "StayNearGoal")) {
            return ActivityClass.MANDATORY_COMMAND;
        }
        if (className.endsWith("EatFoodGoal")) {
            return ActivityClass.MANDATORY_SURVIVAL;
        }
        if (endsWithAny(className,
                "SkepticalWatchGoal", "FriendlyGreetGoal", "DoorOperationGoal")) {
            return ActivityClass.SOCIAL_REFLEX;
        }
        if (className.endsWith("FollowLovedOneGoal")) {
            return ActivityClass.SOCIAL_TRAVEL;
        }
        if (endsWithAny(className, "SeekAmmoGoal", "BlockArrowsGoal")) {
            return ActivityClass.COMBAT_PREP;
        }
        if (isKnownCombatGoal(className)) {
            return ActivityClass.MANDATORY_COMBAT;
        }
        if (endsWithAny(className,
                "RaidContainersGoal", "RaidArmorStandsGoal", "CollectFloorItemsGoal")) {
            return ActivityClass.SCAVENGE_LOOT;
        }
        if (className.endsWith("HarvestCropsGoal")) {
            return ActivityClass.FARMING;
        }
        if (endsWithAny(className, "AdvanceCarriageGoal", "CrossGroupGapGoal")) {
            return ActivityClass.DUNGEON_TRAIN;
        }
        if (endsWithAny(className, "FloatGoal", "PlayerMobDoorGoal", "DigThroughGoal")) {
            return ActivityClass.PASSIVE_HELPER;
        }
        return ActivityClass.UNKNOWN_ACTIVE;
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
        ActivityClass activity = activityClass(goal, mob, store, mobId, now);
        switch (activity) {
            case MANDATORY_SAFETY:
                return MoveHolderClassification.PROTECTED_SAFETY_RECOVERY;
            case SHELTER_HOLD:
                return MoveHolderClassification.PROTECTED_SAFETY_RECOVERY;
            case MANDATORY_COMMAND:
                return MoveHolderClassification.PROTECTED_PLAYER_ORDER;
            case MANDATORY_SURVIVAL:
                return MoveHolderClassification.PROTECTED_LOW_FOOD;
            case SOCIAL_REFLEX:
                return MoveHolderClassification.PROTECTED_FINITE;
            case DISCRETIONARY_SOCIAL:
                return MoveHolderClassification.ORDINARY_HOST_WORK;
            case SOCIAL_TRAVEL, COMBAT_PREP, SCAVENGE_LOOT, FARMING, DUNGEON_TRAIN:
                return MoveHolderClassification.ORDINARY_HOST_WORK;
            // V2-F. Deliberately NOT cooperative project work: that classification has a stronger
            // contract - an arbiter-recognised MiningGoalKind participant doing downstream work the
            // project wants - and TradeWithVillagerGoal has neither a project binding nor the
            // arbiter's blessing. Granting it lease-pausing semantics would manufacture cooperation
            // out of shared demand alone. Mining sees a real MOVE contender and the lease ages.
            //
            // If a future slice binds a particular trade attempt to an active mining project, add a
            // conditional project-provenance path there rather than declaring all village trading
            // cooperative here.
            case VILLAGE_TRADE:
                return MoveHolderClassification.ORDINARY_HOST_WORK;
            case MANDATORY_COMBAT:
                return MoveHolderClassification.PROTECTED_COMBAT;
            default:
                break;
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

    private static boolean isProtectedCombatGoal(String className, @Nullable Mob mob) {
        if (mob != null
                && mob.getTarget() != null
                && (className.contains("MeleeAttackGoal")
                        || className.contains("RangedAttackGoal")
                        || className.contains("BowAttackGoal"))) {
            return true;
        }
        return isKnownCombatGoal(className);
    }

    private static boolean isKnownCombatGoal(String className) {
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
