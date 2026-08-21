package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Task-53 — taxonomy scenarios 11–12 (D-VR-082-A1). */
class VillageWorkTaxonomyTest {

    private static ActivityObservationService.Observation observing(ActivityClass... classes) {
        return ActivityObservationService.summarize(List.of(classes));
    }

    /** Scenario 11 — VILLAGE_WORK blocks fresh discretionary selection. */
    @Test
    void scenario11_villageWorkBlocksDiscretionarySelection() {
        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(
                observing(ActivityClass.VILLAGE_WORK), false));
        assertTrue(DiscretionaryEligibility.blocksDiscretionaryChoice(ActivityClass.VILLAGE_WORK));
    }

    /** Scenario 12 — MAINTENANCE alone does not block (P4 asymmetry). */
    @Test
    void scenario12_maintenanceDoesNotBlock() {
        assertTrue(DiscretionaryEligibility.isDiscretionaryEligible(
                observing(ActivityClass.MAINTENANCE), false));
        assertFalse(DiscretionaryEligibility.blocksDiscretionaryChoice(ActivityClass.MAINTENANCE));
    }
}
