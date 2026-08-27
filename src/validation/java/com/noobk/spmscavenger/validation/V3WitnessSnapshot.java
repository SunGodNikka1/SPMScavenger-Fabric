package com.noobk.spmscavenger.validation;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Shared immutable, passive Task-59 snapshot consumed by inspect and campaign automation. */
record V3WitnessSnapshot(
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
        V3Gate0Assessment.Result gate0,
        boolean daytime,
        long dayTime,
        boolean shelterHold,
        V3RowPrecondition.Result rowPrecondition) {

    V3WitnessSnapshot {
        position = position.immutable();
        runningGoals = List.copyOf(runningGoals);
        activeClasses = Set.copyOf(activeClasses);
        pendingClaim = pendingClaim == null ? Optional.empty() : pendingClaim;
        settlement = settlement == null ? Optional.empty() : settlement;
        facts = facts == null ? Optional.empty() : facts;
    }

    static V3WitnessSnapshot capture(ServerLevel level, Mob mob) {
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
        Gate0Snapshot gate0 = captureGate0(level, mob);
        boolean daytime = level.isDay();
        boolean shelterHold = observation.activeClasses().contains(ActivityClass.SHELTER_HOLD);
        V3RowPrecondition.Result rowPrecondition =
                V3RowPrecondition.evaluate(daytime, shelterHold);
        return new V3WitnessSnapshot(
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
                gate0.settlement(),
                gate0.facts(),
                gate0.assessment(),
                daytime,
                level.getDayTime(),
                shelterHold,
                rowPrecondition);
    }

    List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("=== V3 Runtime Witness Snapshot ===");
        lines.add("tick=" + tick + " target=" + targetName
                + " uuid=" + targetId + " dimension=" + dimension);
        lines.add("profile=" + profile.serialized() + " combatTarget=" + combatTarget);
        lines.add("position=" + position.toShortString());
        lines.add("running=" + runningGoals);
        lines.add("activeClasses=" + activeClasses);
        lines.add(pendingClaim.map(value -> "pendingClaim=YES consumer=" + value.consumerKey()
                        + " route=" + value.routeIdentity()
                        + " generation=" + value.generation()
                        + " openedAt=" + value.openedAt()
                        + " expiresAt=" + value.expiresAt()
                        + " ticksRemaining=" + Math.max(0L, value.expiresAt() - tick))
                .orElse("pendingClaim=NO"));
        lines.add("mandatoryPermission=" + (mandatoryPermission.eligible() ? "ALLOW" : "DENY")
                + " cause=" + mandatoryPermission.cause());
        lines.add("villageWork=" + (villageWork.permitted() ? "ALLOW" : "DENY")
                + " cause=" + villageWork.cause()
                + " authorityCause=" + villageWork.authorityCause());
        lines.add("settlement observed: " + (settlement.isPresent() ? "YES" : "NO"));
        lines.add(settlement
                .map(value -> "settlement anchor: " + value.anchor().toShortString()
                        + " settlement identity: " + value.identity())
                .orElse("settlement anchor: UNAVAILABLE settlement identity: UNAVAILABLE"));
        lines.add(facts
                .map(value -> "populationFacts adultVillagerCount=" + value.adultVillagerCount()
                        + " totalUsableHomeCapacity=" + value.totalUsableHomeCapacity()
                        + " claimedHomeCount=" + value.claimedHomeCount()
                        + " currentFreeHomeCapacity=" + value.currentFreeHomeCapacity()
                        + " completeness=" + value.completeness()
                        + " freshness=" + value.freshness()
                        + " observedAtTick=" + value.observedAtTick())
                .orElse("populationFacts=UNAVAILABLE completeness=UNAVAILABLE freshness=UNAVAILABLE"));
        lines.add("Gate0=" + gate0.verdict() + " reason=" + gate0.reason());
        lines.add("daytime=" + (daytime ? "YES" : "NO")
                + " dayTime=" + dayTime
                + " shelterHold=" + (shelterHold ? "YES" : "NO"));
        lines.add("RowPrecondition=" + rowPrecondition.verdict()
                + " reason=" + rowPrecondition.reason());
        return List.copyOf(lines);
    }

    String transitionKey() {
        return "active=" + activeClasses
                + "|combat=" + combatTarget
                + "|claim=" + pendingClaim.map(value -> value.consumerKey() + "/" + value.routeIdentity())
                        .orElse("none")
                + "|mandatory=" + mandatoryPermission.cause()
                + "|village=" + villageWork.cause()
                + "|gate0=" + gate0.verdict()
                + "|row=" + rowPrecondition.verdict();
    }

    String compactLine() {
        return "pos=" + position.toShortString()
                + " active=" + activeClasses
                + " combat=" + combatTarget
                + " claim=" + (pendingClaim.isPresent() ? "YES" : "NO")
                + " villageWork=" + (villageWork.permitted() ? "ALLOW" : "DENY")
                + "/" + villageWork.cause()
                + " Gate0=" + gate0.verdict()
                + " RowPrecondition=" + rowPrecondition.verdict();
    }

    private static Gate0Snapshot captureGate0(ServerLevel level, Mob mob) {
        VillageMemorySavedData memory = VillageMemorySavedData.peekInDimension(level);
        Optional<MobVillageMemory> mobMemory = memory == null
                ? Optional.empty()
                : memory.peek(mob.getUUID());
        Optional<RememberedSettlement> settlement = mobMemory.flatMap(value ->
                nearestCurrentSettlement(value, mob.blockPosition(), level));
        Optional<VillageWorkFacts> facts = settlement.flatMap(value ->
                VillageWorkFactsService.peekReadOnly(level, value.identity()));
        V3Gate0Assessment.Result assessment =
                V3Gate0Assessment.evaluate(settlement.isPresent(), facts);
        return new Gate0Snapshot(settlement, facts, assessment);
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
            String goal = wrapped.getGoal().getClass().getSimpleName();
            String activity = MoveHolderClassifier.activityClass(
                    wrapped.getGoal(), mob, mining, mob.getUUID(), now).name();
            out.add(goal + ":" + activity);
            if (out.size() >= 16) {
                break;
            }
        }
        return List.copyOf(out);
    }

    record RememberedSettlement(BlockPos anchor, SettlementIdentity identity) {
        RememberedSettlement {
            anchor = anchor.immutable();
        }
    }

    private record Gate0Snapshot(
            Optional<RememberedSettlement> settlement,
            Optional<VillageWorkFacts> facts,
            V3Gate0Assessment.Result assessment) {
    }
}
