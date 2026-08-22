package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** CLOSE-57-1 — bounded HOME probe enumeration per recipient. */
class BreederLocalHomeProofBudgetTest {

    @Test
    void close57_1_homeProbeBudgetCapsWorkWhenNoReachableHome() {
        AtomicInteger maxProbe = new AtomicInteger();
        BreederLocalHomeProof.VacantHomeCandidateSource manyHomes = visitor -> {
            for (int i = 0; i < PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4; i++) {
                visitor.accept(null);
            }
        };

        assertFalse(BreederLocalHomeProof.anyReachableVacantHome(
                manyHomes, probe -> {
                    maxProbe.set(Math.max(maxProbe.get(), probe));
                    return false;
                }));
        assertEquals(
                PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT,
                maxProbe.get(),
                "must not path-probe beyond the per-recipient budget when none succeed");
    }

    @Test
    void close57_1_firstReachableHomeWinsDespiteMoreHomesExisting() {
        AtomicInteger maxProbe = new AtomicInteger();
        BreederLocalHomeProof.VacantHomeCandidateSource manyHomes = visitor -> {
            for (int i = 0; i < PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT + 4; i++) {
                visitor.accept(null);
            }
        };

        assertTrue(BreederLocalHomeProof.anyReachableVacantHome(
                manyHomes, probe -> {
                    maxProbe.set(Math.max(maxProbe.get(), probe));
                    return probe == 1;
                }),
                "existential proof must not be invalidated by unexamined HOME records");
        assertEquals(1, maxProbe.get(), "must short-circuit after the first reachable HOME");
    }
}
