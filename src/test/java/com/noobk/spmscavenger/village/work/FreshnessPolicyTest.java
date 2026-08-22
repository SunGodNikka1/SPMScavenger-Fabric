package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class FreshnessPolicyTest {

    @Test
    void mustHappen_recentObservationIsFresh() {
        assertEquals(
                WorkFactsFreshness.FRESH,
                FreshnessPolicy.classify(100L, 100L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS));
    }

    @Test
    void mustHappen_expiredObservationIsStale() {
        assertEquals(
                WorkFactsFreshness.STALE,
                FreshnessPolicy.classify(
                        100L, 100L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS + 1));
    }

    @Test
    void mustHappen_peekReclassifiesStaleFacts() {
        VillageWorkFacts fresh = new VillageWorkFacts(
                SettlementIdentity.of(Level.OVERWORLD, BlockPos.ZERO),
                2,
                3,
                1,
                2,
                50L,
                WorkFactsCompleteness.COMPLETE,
                WorkFactsFreshness.FRESH);
        VillageWorkFacts stale = FreshnessPolicy.apply(fresh, 50L + VillageWorkTuning.FRESHNESS_WINDOW_TICKS + 5);
        assertEquals(WorkFactsFreshness.STALE, stale.freshness());
        assertTrue(stale.completeness() == WorkFactsCompleteness.COMPLETE);
    }
}
