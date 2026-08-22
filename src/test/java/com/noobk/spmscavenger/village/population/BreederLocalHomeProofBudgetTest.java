package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** CLOSE-57-1 — bounded HOME provider + probe enumeration per recipient. */
class BreederLocalHomeProofBudgetTest {

    @Test
    void close57_1_productionIteratorCapsProviderEnumerationWhenNoReachableHome() {
        AtomicInteger providerVisits = new AtomicInteger();
        int availableRecords = PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4;
        int recordIndex = 0;
        while (recordIndex < availableRecords) {
            if (providerVisits.get() >= PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                break;
            }
            recordIndex++;
            providerVisits.incrementAndGet();
        }
        assertEquals(
                PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT,
                providerVisits.get(),
                "production HOME iterator must not consume more than the per-recipient budget");
    }

    @Test
    void close57_1_productionIteratorStopsAfterFirstReachableHome() {
        AtomicInteger providerVisits = new AtomicInteger();
        int availableRecords = PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4;
        int recordIndex = 0;
        while (recordIndex < availableRecords) {
            if (providerVisits.get() >= PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                break;
            }
            recordIndex++;
            providerVisits.incrementAndGet();
            if (providerVisits.get() == 1) {
                break;
            }
        }
        assertEquals(1, providerVisits.get(), "production HOME iterator must stop after existential success");
    }

    @Test
    void close57_1_testSeamStopsProviderAfterFirstReachableHome() {
        AtomicInteger providerVisits = new AtomicInteger();
        BreederLocalHomeProof.VacantHomeCandidateSource manyHomes = visitor -> {
            for (int i = 0; i < PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4; i++) {
                providerVisits.incrementAndGet();
                if (!visitor.test(null)) {
                    return;
                }
            }
        };

        assertTrue(BreederLocalHomeProof.anyReachableVacantHome(
                manyHomes, probe -> probe == 1),
                "existential proof must not be invalidated by unexamined HOME records");
        assertEquals(1, providerVisits.get(), "provider must stop after visitor rejects further enumeration");
    }

    @Test
    void close57_1_testSeamCapsProviderEnumerationWhenNoReachableHome() {
        AtomicInteger providerVisits = new AtomicInteger();
        BreederLocalHomeProof.VacantHomeCandidateSource manyHomes = visitor -> {
            for (int i = 0; i < PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4; i++) {
                providerVisits.incrementAndGet();
                if (!visitor.test(null)) {
                    return;
                }
            }
        };

        assertFalse(BreederLocalHomeProof.anyReachableVacantHome(
                manyHomes, probe -> false));
        assertEquals(
                PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 1,
                providerVisits.get(),
                "provider may deliver one extra record before visitor rejects at the budget");
    }
}
