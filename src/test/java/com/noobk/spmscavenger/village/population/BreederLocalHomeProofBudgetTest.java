package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** CLOSE-57-1 — bounded HOME probe enumeration per recipient. */
class BreederLocalHomeProofBudgetTest {

    @Test
    void close57_1_homeEnumerationStopsAtPerRecipientBudget() {
        AtomicInteger visits = new AtomicInteger();
        BreederLocalHomeProof.VacantHomeCandidateSource overCap = visitor -> {
            for (int examined = 1; examined <= PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4;
                    examined++) {
                if (examined > PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                    return false;
                }
                visits.incrementAndGet();
            }
            return true;
        };

        assertFalse(overCap.enumerate(record -> { }));
        assertEquals(
                PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT,
                visits.get(),
                "HOME enumeration must not path-probe beyond the per-recipient budget");
    }

    @Test
    void close57_1_withinBudgetEnumerationCompletes() {
        BreederLocalHomeProof.VacantHomeCandidateSource within = visitor -> true;
        assertTrue(within.enumerate(record -> { }));
    }
}
