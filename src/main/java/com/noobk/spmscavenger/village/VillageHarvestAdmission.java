package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VillageWorkAdmission} wiring for the harvest episode goal.
 */
public final class VillageHarvestAdmission {

    private VillageHarvestAdmission() {
    }

    public static boolean permits(Mob mob, GoalSelector selector, @Nullable Goal excludeFromObservation) {
        if (!(mob.level() instanceof ServerLevel level)) {
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
}
