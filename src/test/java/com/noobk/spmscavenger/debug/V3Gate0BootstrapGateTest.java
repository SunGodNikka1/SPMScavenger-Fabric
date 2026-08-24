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

class V3Gate0BootstrapGateTest {

    @Test
    void readableHomeDeficitsRemainDiagnosticBeforeBootstrapBoundary() {
        assertVerdict(20L, 0, V3Gate0BootstrapGate.Verdict.WAITING_BOOTSTRAP);
        assertVerdict(60L, 1, V3Gate0BootstrapGate.Verdict.WAITING_BOOTSTRAP);
        assertVerdict(119L, 1, V3Gate0BootstrapGate.Verdict.WAITING_BOOTSTRAP);
    }

    @Test
    void exactBootstrapBoundaryAdjudicatesReadableThresholds() {
        assertVerdict(120L, 2, V3Gate0BootstrapGate.Verdict.PASS);
        assertVerdict(120L, 1, V3Gate0BootstrapGate.Verdict.FIXTURE_FAILURE);
    }

    @Test
    void elapsedTimeIsMeasuredFromSuccessfulFixtureExecution() {
        V3Gate0Assessment.Result passing = assessment(2);
        assertEquals(V3Gate0BootstrapGate.Verdict.WAITING_BOOTSTRAP,
                V3Gate0BootstrapGate.evaluate(1_000L, 1_119L, passing).verdict());
        assertEquals(V3Gate0BootstrapGate.Verdict.PASS,
                V3Gate0BootstrapGate.evaluate(1_000L, 1_120L, passing).verdict());
    }

    private static void assertVerdict(
            long elapsedTicks, int claimedHomes, V3Gate0BootstrapGate.Verdict expected) {
        V3Gate0Assessment.Result assessment = assessment(claimedHomes);
        assertEquals(expected,
                V3Gate0BootstrapGate.evaluate(500L, 500L + elapsedTicks, assessment).verdict());
    }

    private static V3Gate0Assessment.Result assessment(int claimedHomes) {
        return V3Gate0Assessment.evaluate(true, Optional.of(new VillageWorkFacts(
                SettlementIdentity.of(Level.OVERWORLD, BlockPos.ZERO),
                2,
                3,
                claimedHomes,
                3 - claimedHomes,
                100L,
                WorkFactsCompleteness.COMPLETE,
                WorkFactsFreshness.FRESH)));
    }
}
