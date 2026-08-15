package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import com.noobk.spmscavenger.village.VillagePerceptionTuning;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V1-D / VR-T1.5c — village perception observer must not trip UNKNOWN_ACTIVE fail-closed. */
class VillagePerceptionObserverTaxonomyTest {

    @Test
    void mustHappen_villagePerceptionObserverIsPassiveObserver() {
        assertEquals(
                ActivityClass.PASSIVE_OBSERVER,
                MoveHolderClassifier.staticActivityClass(VillagePerceptionObserver.class));
        assertEquals(
                ActivityClass.PASSIVE_OBSERVER,
                MoveHolderClassifier.staticActivityClass(ExplorationActivityGoal.class));
    }

    @Test
    void mustHappen_runningVillagePerceptionObserverDoesNotSetUnknownActive() {
        VillagePerceptionObserver observer = new VillagePerceptionObserver(
                null,
                new VillagePerceptionEnqueueDebounce(),
                new PhasedScanClock(1, VillagePerceptionTuning.HEARTBEAT_TICKS, 0));
        var observation = ActivityObservationService.observeRunningGoals(
                List.of(observer), null, null, UUID.randomUUID(), 0L);

        assertTrue(observation.activeClasses().contains(ActivityClass.PASSIVE_OBSERVER));
        assertFalse(observation.unknownActive());
        assertTrue(DiscretionaryEligibility.isDiscretionaryEligible(observation, false));
    }
}
