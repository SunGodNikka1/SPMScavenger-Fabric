package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class VillageWorkObservationKernelBudgetTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);

    @Test
    void close56_2_homePoiEnumerationStopsAtBudgetBeforeIncomplete() {
        AtomicInteger providerVisits = new AtomicInteger();
        VillageWorkObservationKernel.HomePoiCandidateSource overCap = visitor -> {
            for (int examined = 1; examined <= VillageWorkTuning.MAX_HOME_POIS_PER_OBSERVATION + 5; examined++) {
                if (examined > VillageWorkTuning.MAX_HOME_POIS_PER_OBSERVATION) {
                    return false;
                }
                providerVisits.incrementAndGet();
            }
            return true;
        };

        VillageWorkObservationKernel.Counts counts = VillageWorkObservationKernel.countSettlementEvidence(
                null,
                ANCHOR,
                overCap,
                visitor -> true,
                (level, pos, anchor) -> true);

        assertEquals(WorkFactsCompleteness.INCOMPLETE, counts.completeness());
        assertEquals(
                VillageWorkTuning.MAX_HOME_POIS_PER_OBSERVATION,
                providerVisits.get(),
                "HOME provider must not examine beyond the evidence budget");
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                factsFrom(counts, ANCHOR)));
    }

    @Test
    void close56_2_villagerEnumerationStopsAtBudgetBeforeIncomplete() {
        AtomicInteger providerVisits = new AtomicInteger();
        VillageWorkObservationKernel.AdultVillagerCandidateSource overCap = visitor -> {
            for (int match = 1; match <= VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION + 4; match++) {
                if (match > VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION) {
                    return false;
                }
                providerVisits.incrementAndGet();
            }
            return true;
        };

        VillageWorkObservationKernel.Counts counts = VillageWorkObservationKernel.countSettlementEvidence(
                null,
                ANCHOR,
                visitor -> true,
                overCap,
                (level, pos, anchor) -> true);

        assertEquals(WorkFactsCompleteness.INCOMPLETE, counts.completeness());
        assertEquals(
                VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION,
                providerVisits.get(),
                "villager provider must not examine beyond the evidence budget");
        assertFalse(PopulationSupportVacancyPolicy.isPopulationSupportCandidate(
                factsFrom(counts, ANCHOR)));
    }

    @Test
    void close56_2_withinBudgetEnumerationCompletes() {
        VillageWorkObservationKernel.Counts counts = VillageWorkObservationKernel.countSettlementEvidence(
                null,
                ANCHOR,
                visitor -> true,
                visitor -> true,
                (level, pos, anchor) -> true);

        assertEquals(WorkFactsCompleteness.COMPLETE, counts.completeness());
    }

    private static VillageWorkFacts factsFrom(
            VillageWorkObservationKernel.Counts counts, BlockPos anchor) {
        return new VillageWorkFacts(
                SettlementIdentity.of(net.minecraft.world.level.Level.OVERWORLD, anchor),
                counts.adultVillagerCount(),
                counts.totalUsableHomeCapacity(),
                counts.claimedHomeCount(),
                counts.currentFreeHomeCapacity(),
                0L,
                counts.completeness(),
                WorkFactsFreshness.FRESH);
    }
}
