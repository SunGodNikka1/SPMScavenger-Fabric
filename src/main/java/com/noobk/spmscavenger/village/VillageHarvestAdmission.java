package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.EnumSet;

/**
 * {@link VillageWorkAdmission} wiring for the harvest episode goal.
 *
 * <p>During continuation, {@link ActivityClass#VILLAGE_WORK} is excluded from the observation so
 * this running goal does not deny itself.
 */
public final class VillageHarvestAdmission {

    private VillageHarvestAdmission() {
    }

    public static boolean permits(Mob mob, GoalSelector selector, boolean continuation) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        ActivityObservationService.Observation observation = continuation
                ? observeExcludingVillageWork(selector, mob, store, now)
                : ActivityObservationService.observe(selector, mob, store, now);
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

    private static ActivityObservationService.Observation observeExcludingVillageWork(
            GoalSelector selector,
            Mob mob,
            MiningProjectSavedData store,
            long now) {
        EnumSet<ActivityClass> active = EnumSet.noneOf(ActivityClass.class);
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            Goal goal = wrapped.getGoal();
            ActivityClass activity = MoveHolderClassifier.activityClass(
                    goal, mob, store, mob.getUUID(), now);
            if (activity == ActivityClass.VILLAGE_WORK) {
                continue;
            }
            active.add(activity);
        }
        return ActivityObservationService.summarize(active);
    }
}
