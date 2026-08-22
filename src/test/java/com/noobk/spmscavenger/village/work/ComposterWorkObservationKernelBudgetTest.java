package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** Task-58 — bounded composter POI enumeration (T58-3). */
class ComposterWorkObservationKernelBudgetTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);

    @Test
    void t58_3_composterEnumerationStopsAtBudgetBeforeIncomplete() {
        AtomicInteger providerVisits = new AtomicInteger();
        ComposterWorkObservationKernel.ComposterPoiCandidateSource overCap = visitor -> {
            for (int examined = 1; examined <= VillageWorkTuning.MAX_COMPOSTERS_PER_OBSERVATION + 5; examined++) {
                if (examined > VillageWorkTuning.MAX_COMPOSTERS_PER_OBSERVATION) {
                    return false;
                }
                providerVisits.incrementAndGet();
            }
            return true;
        };

        ComposterWorkObservationKernel.Result result = ComposterWorkObservationKernel.enumerateComposters(
                null,
                ANCHOR,
                overCap,
                (level, pos, anchor) -> true);

        assertEquals(WorkFactsCompleteness.INCOMPLETE, result.completeness());
        assertEquals(
                VillageWorkTuning.MAX_COMPOSTERS_PER_OBSERVATION,
                providerVisits.get(),
                "composter provider must not examine beyond the evidence budget");
        assertTrue(result.composterPositions().isEmpty());
    }

    @Test
    void t58_3_withinBudgetEnumerationCompletes() {
        ComposterWorkObservationKernel.Result result = ComposterWorkObservationKernel.enumerateComposters(
                null,
                ANCHOR,
                visitor -> true,
                (level, pos, anchor) -> true);

        assertEquals(WorkFactsCompleteness.COMPLETE, result.completeness());
    }
}
