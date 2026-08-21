package com.noobk.spmscavenger.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.goal.VillageHarvestEpisodeGoal;
import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Task-55 R1-3 — exact-goal observation exclusion. */
class ActivityObservationServiceExclusionTest {

    private static final UUID MOB_ID = UUID.randomUUID();

    @Test
    void selfVillageWorkOnlyDoesNotDenyItself() {
        Goal self = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        ActivityObservationService.Observation full = ActivityObservationService.observeRunningGoals(
                List.of(self), null, null, MOB_ID, 0L);
        ActivityObservationService.Observation excluded =
                ActivityObservationService.observeRunningGoalsExcluding(
                        List.of(self), self, null, null, MOB_ID, 0L);

        assertTrue(full.activeClasses().contains(ActivityClass.VILLAGE_WORK));
        assertFalse(excluded.activeClasses().contains(ActivityClass.VILLAGE_WORK));
        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(full, false));
        assertTrue(DiscretionaryEligibility.isDiscretionaryEligible(excluded, false));
    }

    @Test
    void selfPlusAnotherVillageWorkStillBlocks() {
        Goal self = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        Goal other = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        ActivityObservationService.Observation observation =
                ActivityObservationService.observeRunningGoalsExcluding(
                        List.of(self, other), self, null, null, MOB_ID, 0L);
        assertTrue(observation.activeClasses().contains(ActivityClass.VILLAGE_WORK));
        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(observation, false));
    }

    @Test
    void selfPlusUnknownActiveStillFailsClosed() {
        Goal self = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        Goal unknown = new UnknownGoal();
        ActivityObservationService.Observation observation =
                ActivityObservationService.observeRunningGoalsExcluding(
                        List.of(self, unknown), self, null, null, MOB_ID, 0L);
        assertTrue(observation.unknownActive());
        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(observation, false));
    }

    @Test
    void exclusionUsesMoveHolderClassifierNotForkedTaxonomy() {
        Goal self = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        Goal other = new VillageHarvestEpisodeGoal(null, new GoalSelector(null), 1.0);
        ActivityObservationService.Observation all = ActivityObservationService.observeRunningGoals(
                List.of(self, other), null, null, MOB_ID, 0L);
        ActivityObservationService.Observation excluded =
                ActivityObservationService.observeRunningGoalsExcluding(
                        List.of(self, other), self, null, null, MOB_ID, 0L);
        assertEquals(all.activeClasses(), excluded.activeClasses());
    }

    private static final class UnknownGoal extends Goal {
        @Override
        public boolean canUse() {
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
