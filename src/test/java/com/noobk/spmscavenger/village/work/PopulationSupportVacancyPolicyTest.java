package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class PopulationSupportVacancyPolicyTest {

    private static VillageWorkFacts facts(
            int adults, int free, WorkFactsCompleteness completeness, WorkFactsFreshness freshness) {
        return new VillageWorkFacts(
                SettlementIdentity.of(Level.OVERWORLD, BlockPos.ZERO),
                adults,
                4,
                4 - free,
                free,
                100L,
                completeness,
                freshness);
    }

    @Test
    void mustHappen_twoAdultsAndFreeHomeIsCandidate() {
        assertTrue(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                facts(2, 1, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)));
    }

    @Test
    void mustNotHappen_allBedsClaimedIsNotCandidate() {
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                facts(2, 0, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)));
    }

    @Test
    void mustNotHappen_singleAdultIsNotCandidate() {
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                facts(1, 2, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)));
    }

    @Test
    void mustNotHappen_staleFactsFailClosed() {
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                facts(3, 2, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.STALE)));
    }

    @Test
    void mustNotHappen_incompleteFactsFailClosed() {
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                facts(3, 2, WorkFactsCompleteness.INCOMPLETE, WorkFactsFreshness.FRESH)));
    }

    @Test
    void mustNotHappen_subtractionHeadroomIsNotUsed() {
        // 4 beds, 6 adults, 2 free — old subtraction formula would be 0; vacancy policy allows candidate.
        VillageWorkFacts overCapacity = facts(6, 2, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH);
        assertTrue(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(overCapacity));
        assertEquals(2, overCapacity.currentFreeHomeCapacity());
    }
}
