package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** CLOSE-57-1 — bounded villager expensive-probe enumeration. */
class PopulationFoodRecipientProbeBudgetTest {

    @Test
    void close57_1_villagerProbeBudgetStopsBeforeUnboundedWork() {
        AtomicInteger expensiveProbes = new AtomicInteger();
        PopulationFoodRecipientSelector.VillagerRecipientCandidateSource manyVillagers = visitor -> {
            for (int i = 0; i < 100; i++) {
                if (!visitor.test(null)) {
                    return false;
                }
            }
            return true;
        };

        boolean enumerationComplete = manyVillagers.enumerate(villager -> {
            if (expensiveProbes.get() >= PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES) {
                return false;
            }
            expensiveProbes.incrementAndGet();
            return true;
        });

        assertFalse(enumerationComplete);
        assertEquals(
                PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES,
                expensiveProbes.get(),
                "expensive HOME/path probes must not run beyond MAX_RECIPIENT_CANDIDATES");
    }

    @Test
    void close57_1_largeSettlementStillInspectsNearestCap() {
        AtomicInteger inspected = new AtomicInteger();
        PopulationFoodRecipientSelector.VillagerRecipientCandidateSource nineAdults = visitor -> {
            int limit = Math.min(9, PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES);
            for (int i = 0; i < limit; i++) {
                inspected.incrementAndGet();
                if (!visitor.test(null)) {
                    return false;
                }
            }
            return true;
        };

        assertTrue(nineAdults.enumerate(villager -> true));
        assertEquals(
                PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES,
                inspected.get(),
                "settlements larger than the cap must still inspect the nearest K adults");
    }
}
