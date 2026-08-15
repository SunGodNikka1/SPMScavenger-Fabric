package com.noobk.spmscavenger.activity;

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
import com.noobk.spmscavenger.goal.TrackedLocalWanderGoal;
import com.noobk.spmscavenger.goal.TunnelSearchGoal;
import com.noobk.spmscavenger.goal.VillagePerceptionObserver;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GAO-0 taxonomy coverage without constructing world-bound Goal implementations. */
class ActivityTaxonomyTest {

    @Test
    void addonGoalsHavePinnedStaticClasses() {
        assertClass(ActivityClass.MANDATORY_SAFETY, EnvironmentalEscapeGoal.class);
        assertClass(ActivityClass.MANDATORY_SAFETY, SeekShelterGoal.class);
        assertClass(ActivityClass.PROJECT_EXECUTION, ControlledDescentGoal.class);
        assertClass(ActivityClass.PROJECT_EXECUTION, TunnelSearchGoal.class);
        assertClass(ActivityClass.SCAVENGE_WORK, GatherResourcesGoal.class);
        assertClass(ActivityClass.SCAVENGE_WORK, CraftTorchesGoal.class);
        assertClass(ActivityClass.SCAVENGE_WORK, SmeltAtFurnaceGoal.class);
        assertClass(ActivityClass.MAINTENANCE, PlaceTorchGoal.class);
        assertClass(ActivityClass.REST_APPROACH, CampfireGoal.class);
        assertClass(ActivityClass.EXPEDITION, ExploringGoal.class);
        assertClass(ActivityClass.IDLE_CANDIDATE, TrackedLocalWanderGoal.class);
        assertClass(ActivityClass.PASSIVE_COSMETIC, AnticsGoal.class);
        assertClass(ActivityClass.PASSIVE_OBSERVER, ExplorationActivityGoal.class);
        assertClass(ActivityClass.PASSIVE_OBSERVER, VillagePerceptionObserver.class);
    }

    @Test
    void passiveObserverDoesNotBlockDiscretionaryEligibility() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.PASSIVE_OBSERVER));

        assertFalse(observation.unknownActive());
        assertTrue(DiscretionaryEligibility.isDiscretionaryEligible(observation, false));
        assertEquals(
                com.noobk.spmscavenger.opinion.InvalidationCause.NONE,
                DiscretionaryEligibility.invalidationForObservation(observation, false));
    }

    @Test
    void unknownGoalStillFailsClosed() {
        assertClass(ActivityClass.UNKNOWN_ACTIVE, CompletelyUnknownGoal.class);

        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.UNKNOWN_ACTIVE));

        assertTrue(observation.unknownActive());
        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(observation, false));
    }

    @Test
    void hostGoalsUseTheSharedClassifierTaxonomy() {
        assertClass(ActivityClass.SOCIAL_TRAVEL, FollowLovedOneGoal.class);
        assertClass(ActivityClass.SCAVENGE_LOOT, RaidContainersGoal.class);
        assertClass(ActivityClass.FARMING, HarvestCropsGoal.class);
        assertClass(ActivityClass.DUNGEON_TRAIN, AdvanceCarriageGoal.class);
        assertClass(ActivityClass.MANDATORY_SURVIVAL, EatFoodGoal.class);
        assertClass(ActivityClass.MANDATORY_COMMAND, CommandedActionGoal.class);
        assertClass(ActivityClass.MANDATORY_COMBAT, WeaponAwareAttackGoal.class);
        assertClass(ActivityClass.PASSIVE_HELPER, PlayerMobDoorGoal.class);
        assertClass(ActivityClass.SOCIAL_REFLEX, DoorOperationGoal.class);
        assertClass(ActivityClass.UNKNOWN_ACTIVE, CompletelyUnknownGoal.class);
    }

    @Test
    void shelterHoldIsSchedulerAuthorityButNotDiscretionaryRest() {
        assertFalse(ActivityClass.SHELTER_HOLD.isRest());
        assertTrue(ActivityClass.SHELTER_HOLD.isSchedulerOccupant());
    }

    private static void assertClass(ActivityClass expected, Class<? extends Goal> goalType) {
        assertEquals(expected, MoveHolderClassifier.staticActivityClass(goalType), goalType.getName());
    }

    private abstract static class StubGoal extends Goal {
        @Override
        public boolean canUse() {
            return true;
        }
    }

    private static final class FollowLovedOneGoal extends StubGoal {}
    private static final class RaidContainersGoal extends StubGoal {}
    private static final class HarvestCropsGoal extends StubGoal {}
    private static final class AdvanceCarriageGoal extends StubGoal {}
    private static final class EatFoodGoal extends StubGoal {}
    private static final class CommandedActionGoal extends StubGoal {}
    private static final class WeaponAwareAttackGoal extends StubGoal {}
    private static final class PlayerMobDoorGoal extends StubGoal {}
    private static final class DoorOperationGoal extends StubGoal {}
    private static final class CompletelyUnknownGoal extends StubGoal {}
}
