package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.village.population.PopulationFoodExpendabilityPolicy;
import com.noobk.spmscavenger.village.population.PopulationFoodInterlocks;
import com.noobk.spmscavenger.village.population.PopulationFoodRecipientSelector;
import com.noobk.spmscavenger.village.population.PopulationFoodDeliveryPlan;
import com.noobk.spmscavenger.village.work.PopulationSupportVacancyPolicy;
import com.noobk.spmscavenger.village.work.FreshnessPolicy;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;

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
        VillageWorkFacts facts = FreshnessPolicy.apply(plan.facts(), gameTime);
        if (!PopulationSupportVacancyPolicy.isPopulationSupportCandidate(facts)) {
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
