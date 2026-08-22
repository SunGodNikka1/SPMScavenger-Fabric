package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.village.population.PopulationFoodExpendabilityPolicy;
import com.noobk.spmscavenger.village.population.PopulationFoodInterlocks;
import com.noobk.spmscavenger.village.population.PopulationFoodRecipientSelector;
import com.noobk.spmscavenger.village.population.PopulationFoodDeliveryPlan;
import com.noobk.spmscavenger.village.population.PopulationFoodTuning;
import com.noobk.spmscavenger.village.work.PopulationSupportVacancyPolicy;
import com.noobk.spmscavenger.village.work.FreshnessPolicy;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.VillageWorkFactsService;
import com.noobk.spmscavenger.village.work.WorkFactsCompleteness;
import com.noobk.spmscavenger.village.work.WorkFactsFreshness;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Admission wiring for {@link com.noobk.spmscavenger.goal.PopulationFoodSupportGoal}.
 */
public final class PopulationFoodSupportAdmission {

    private PopulationFoodSupportAdmission() {}

    public static boolean mobGriefingPermits(ServerLevel level) {
        return level != null && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    public static boolean permits(Mob mob, GoalSelector selector, @Nullable Goal excludeFromObservation) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!mobGriefingPermits(level)) {
            return false;
        }
        long now = level.getGameTime();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        ActivityObservationService.Observation observation = excludeFromObservation == null
                ? ActivityObservationService.observe(selector, mob, store, now)
                : ActivityObservationService.observeExcluding(
                        selector, excludeFromObservation, mob, store, now);
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                level.getServer(), mob.getUUID());
        boolean combat = mob.getTarget() != null;
        return VillageWorkAdmission.evaluate(
                profile,
                observation,
                combat,
                MandatoryOwnershipRegistry.liveClaim(mob.getUUID(), now),
                now).permitted();
    }

    /**
     * Whether the mob still remembers the exact settlement anchor from the plan (CLOSE-57-2).
     */
    public static boolean settlementStillRemembered(MobVillageMemory memory, SettlementIdentity settlement) {
        if (memory == null || settlement == null) {
            return false;
        }
        return memory.villages().stream()
                .anyMatch(village -> village.anchor().equals(settlement.anchor()));
    }

    /**
     * Current cache evidence for handoff — never falls back to plan-captured facts (CLOSE-57-2).
     */
    public static boolean currentSettlementEvidence(
            MobVillageMemory memory,
            SettlementIdentity settlement,
            Optional<VillageWorkFacts> peeked,
            long gameTime) {
        if (!settlementStillRemembered(memory, settlement) || peeked.isEmpty()) {
            return false;
        }
        VillageWorkFacts facts = FreshnessPolicy.apply(peeked.get(), gameTime);
        if (facts.completeness() != WorkFactsCompleteness.COMPLETE
                || facts.freshness() != WorkFactsFreshness.FRESH) {
            return false;
        }
        return PopulationSupportVacancyPolicy.isPopulationSupportCandidate(facts);
    }

    /**
     * Route/interaction distance gate immediately before COMMIT (CLOSE-57-3).
     */
    public static boolean withinHandoffDistance(Mob mob, Villager recipient) {
        return mob != null
                && recipient != null
                && mob.distanceToSqr(recipient) < PopulationFoodTuning.REACH_DISTANCE_SQR;
    }

    /**
     * Full handoff preflight — must pass before backpack debit (task-57 §5).
     */
    public static boolean handoffPreflight(
            ServerLevel level,
            Mob mob,
            GoalSelector selector,
            Goal excludeFromObservation,
            PopulationFoodDeliveryPlan plan,
            long gameTime) {
        if (plan == null || !permits(mob, selector, excludeFromObservation)) {
            return false;
        }
        Villager recipient = plan.recipient();
        if (!withinHandoffDistance(mob, recipient)) {
            return false;
        }
        if (!PopulationFoodRecipientSelector.isEligibleAdult(
                recipient, plan.settlement().anchor())) {
            return false;
        }
        if (!PopulationFoodRecipientSelector.needsFood(recipient)) {
            return false;
        }
        if (PopulationFoodInterlocks.blocksHandoff(mob.getUUID(), recipient.getUUID(), gameTime)) {
            return false;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        if (!currentSettlementEvidence(
                memory.get(),
                plan.settlement(),
                VillageWorkFactsService.peek(level, plan.settlement()),
                gameTime)) {
            return false;
        }
        if (!com.noobk.spmscavenger.village.population.BreederLocalHomeProof.hasReachableVacantHome(
                level, recipient)) {
            return false;
        }
        var delivery = plan.delivery();
        return PopulationFoodExpendabilityPolicy.planDelivery(
                        com.noobk.spmscavenger.PlayerMobs.backpack(mob),
                        mob.getMainHandItem(),
                        mob.getOffhandItem(),
                        com.noobk.spmscavenger.village.population.VillagerFoodInventory
                                .inventoryFoodPoints(recipient))
                .filter(offer -> offer.slot() == delivery.slot()
                        && offer.item() == delivery.item()
                        && offer.count() >= delivery.count()
                        && offer.villagerFoodValue() >= delivery.villagerFoodValue())
                .isPresent();
    }
}
