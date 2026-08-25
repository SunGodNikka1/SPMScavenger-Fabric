package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.WorkFactsCompleteness;
import com.noobk.spmscavenger.village.work.WorkFactsFreshness;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class V3Gate0AssessmentTest {

    @Test
    void noSettlementOrFactsIsIncomplete() {
        assertEquals(V3Gate0Assessment.Verdict.INCOMPLETE,
                V3Gate0Assessment.evaluate(false, Optional.empty()).verdict());
        assertEquals(V3Gate0Assessment.Verdict.INCOMPLETE,
                V3Gate0Assessment.evaluate(true, Optional.empty()).verdict());
    }

    @Test
    void incompleteOrStaleFactsRemainIncomplete() {
        assertEquals(V3Gate0Assessment.Verdict.INCOMPLETE,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        2, 3, 2, 1, WorkFactsCompleteness.INCOMPLETE, WorkFactsFreshness.STALE)))
                        .verdict());
        assertEquals(V3Gate0Assessment.Verdict.INCOMPLETE,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        2, 3, 2, 1, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.STALE)))
                        .verdict());
    }

    @Test
    void exactGate0ThresholdsPass() {
        assertEquals(V3Gate0Assessment.Verdict.PASS,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        2, 3, 2, 1, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)))
                        .verdict());
    }

    @Test
    void structuralDeficitsFailButHomeAcquisitionDeficitRemainsDynamic() {
        assertEquals(V3Gate0Assessment.Verdict.FIXTURE_FAILURE,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        1, 3, 2, 1, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)))
                        .verdict());
        assertEquals(V3Gate0Assessment.Verdict.FIXTURE_FAILURE,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        2, 2, 1, 1, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)))
                        .verdict());
        V3Gate0Assessment.Result dynamic = V3Gate0Assessment.evaluate(
                true, Optional.of(facts(
                        2, 3, 1, 2, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)));
        assertEquals(V3Gate0Assessment.Verdict.INCOMPLETE, dynamic.verdict());
        assertEquals(V3Gate0Assessment.EvidenceKind.DYNAMIC_HOME_OCCUPANCY,
                dynamic.evidenceKind());
        assertEquals(V3Gate0Assessment.Verdict.FIXTURE_FAILURE,
                V3Gate0Assessment.evaluate(true, Optional.of(facts(
                        2, 3, 2, 0, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH)))
                        .verdict());
    }

    private static VillageWorkFacts facts(
            int adults,
            int totalHomes,
            int claimedHomes,
            int freeHomes,
            WorkFactsCompleteness completeness,
            WorkFactsFreshness freshness) {
        return new VillageWorkFacts(
                SettlementIdentity.of(Level.OVERWORLD, BlockPos.ZERO),
                adults,
                totalHomes,
                claimedHomes,
                freeHomes,
                100L,
                completeness,
                freshness);
    }
}
