package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnership;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import com.noobk.spmscavenger.village.VillageWorkAdmission;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.VillageWorkFactsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;

/** Immutable one-shot production readout assembled only from passive/non-creating truth providers. */
record GeneralDebugSnapshot(
        long tick,
        String targetName,
        UUID targetId,
        String dimension,
        BlockPos position,
        VillageScenarioProfile profile,
        boolean combatTarget,
        List<String> runningGoals,
        Set<ActivityClass> activeClasses,
        Optional<MandatoryOwnershipClaim> pendingClaim,
        MandatoryOwnership.Permission mandatoryPermission,
        VillageWorkAdmission.Result villageWork,
        Optional<RememberedSettlement> settlement,
        Optional<VillageWorkFacts> facts,
        List<String> inventory) {

    GeneralDebugSnapshot {
        position = position.immutable();
        runningGoals = List.copyOf(runningGoals);
        activeClasses = Set.copyOf(activeClasses);
        pendingClaim = pendingClaim == null ? Optional.empty() : pendingClaim;
        settlement = settlement == null ? Optional.empty() : settlement;
        facts = facts == null ? Optional.empty() : facts;
        inventory = List.copyOf(inventory);
    }

    static GeneralDebugSnapshot capture(ServerLevel level, Mob mob) {
        long now = level.getGameTime();
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        MiningProjectSavedData mining = MiningProjectSavedData.peekReadOnly(level);
        ActivityObservationService.Observation observation =
                ActivityObservationService.observe(selector, mob, mining, now);
        Optional<MandatoryOwnershipClaim> claim =
                MandatoryOwnershipRegistry.peekLiveClaim(mob.getUUID(), now);
        MandatoryOwnership.Permission authority = MandatoryOwnership.evaluate(
                observation, mob.getTarget() != null, claim, now);
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                level.getServer(), mob.getUUID());
        VillageWorkAdmission.Result villageWork = VillageWorkAdmission.evaluate(
                profile, observation, mob.getTarget() != null, claim, now);
        SettlementSnapshot village = captureSettlement(level, mob);
        return new GeneralDebugSnapshot(
                now,
                mob.getName().getString(),
                mob.getUUID(),
                level.dimension().location().toString(),
                mob.blockPosition(),
                profile,
                mob.getTarget() != null,
                runningGoals(selector, mob, mining, now),
                observation.activeClasses(),
                claim,
                authority,
                villageWork,
                village.settlement(),
                village.facts(),
                inventory(PlayerMobs.backpack(mob)));
    }

    List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("=== SPM Scavenger Debug ===");
        lines.add("target=" + targetName + " uuid=" + targetId + " tick=" + tick
                + " dimension=" + dimension + " position=" + position.toShortString());
        lines.add("activity running=" + runningGoals + " activeClasses=" + activeClasses
                + " combatTarget=" + combatTarget);
        lines.add(pendingClaim.map(value -> "progression pendingClaim=YES consumer="
                        + value.consumerKey() + " route=" + value.routeIdentity()
                        + " generation=" + value.generation() + " expiresAt=" + value.expiresAt())
                .orElse("progression pendingClaim=NO"));
        lines.add("authority mandatory=" + (mandatoryPermission.eligible() ? "ALLOW" : "DENY")
                + "/" + mandatoryPermission.cause()
                + " villageWork=" + (villageWork.permitted() ? "ALLOW" : "DENY")
                + "/" + villageWork.cause());
        lines.add("village profile=" + profile.serialized() + " settlement=" + settlement
                .map(value -> value.identity() + " anchor=" + value.anchor().toShortString())
                .orElse("UNAVAILABLE"));
        lines.add(facts.map(value -> "village facts adults=" + value.adultVillagerCount()
                        + " homes=" + value.totalUsableHomeCapacity()
                        + " claimed=" + value.claimedHomeCount()
                        + " free=" + value.currentFreeHomeCapacity()
                        + " completeness=" + value.completeness()
                        + " freshness=" + value.freshness())
                .orElse("village facts=UNAVAILABLE"));
        lines.add("inventory=" + inventory);
        return List.copyOf(lines);
    }

    private static SettlementSnapshot captureSettlement(ServerLevel level, Mob mob) {
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        Optional<MobVillageMemory> memory = data == null
                ? Optional.empty()
                : data.peek(mob.getUUID());
        Optional<RememberedSettlement> settlement = memory.flatMap(value ->
                nearestCurrentSettlement(value, mob.blockPosition(), level));
        Optional<VillageWorkFacts> facts = settlement.flatMap(value ->
                VillageWorkFactsService.peekReadOnly(level, value.identity()));
        return new SettlementSnapshot(settlement, facts);
    }

    private static Optional<RememberedSettlement> nearestCurrentSettlement(
            MobVillageMemory memory, BlockPos mobPos, ServerLevel level) {
        KnownVillage nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (KnownVillage village : memory.villages()) {
            if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
                continue;
            }
            double distance = mobPos.distSqr(village.anchor());
            if (distance < nearestDistance) {
                nearest = village;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }
        return Optional.of(new RememberedSettlement(
                nearest.anchor(), SettlementIdentity.of(level.dimension(), nearest.anchor())));
    }

    private static List<String> runningGoals(
            GoalSelector selector, Mob mob, MiningProjectSavedData mining, long now) {
        List<String> out = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            out.add(wrapped.getGoal().getClass().getSimpleName() + ":"
                    + MoveHolderClassifier.activityClass(
                            wrapped.getGoal(), mob, mining, mob.getUUID(), now));
            if (out.size() >= 16) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static List<String> inventory(Container container) {
        if (container == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                out.add("slot " + slot + "=" + stack.getCount() + "x "
                        + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
        return List.copyOf(out);
    }

    record RememberedSettlement(BlockPos anchor, SettlementIdentity identity) {
        RememberedSettlement {
            anchor = anchor.immutable();
        }
    }

    private record SettlementSnapshot(
            Optional<RememberedSettlement> settlement,
            Optional<VillageWorkFacts> facts) {
    }
}
