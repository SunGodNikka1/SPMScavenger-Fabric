package com.noobk.spmscavenger.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4FixtureLightingGateTest {

    @Test
    void structureMayBeReadyWhileThreadedLightingIsStillPending() {
        V4FixtureGeometryBuilder.Diagnostics diagnostics = structureReadyDiagnostics();

        assertTrue(diagnostics.readyForLightingWait());
        assertFalse(diagnostics.ready());
    }

    @Test
    void waitsBeforeDeadlineWithoutBorrowingAnyBehavioralClock() {
        V4FixtureGeometryBuilder.Diagnostics diagnostics = structureReadyDiagnostics();
        V4FixtureGeometryBuilder.beginLightingWait(diagnostics, 100L, 300L);

        assertEquals(V4FixtureLightingGate.Verdict.WAITING,
                V4FixtureLightingGate.evaluate(false, 299L,
                        diagnostics.lightingWaitDeadline));
        assertEquals(100L, diagnostics.lightingWaitStartedTick);
        assertEquals(300L, diagnostics.lightingWaitDeadline);
    }

    @Test
    void propagatedLightPassWinsEvenAtTheDeadline() {
        assertEquals(V4FixtureLightingGate.Verdict.PASS,
                V4FixtureLightingGate.evaluate(true, 300L, 300L));
    }

    @Test
    void darkFixtureTimesOutAtTheBound() {
        assertEquals(V4FixtureLightingGate.Verdict.TIMEOUT,
                V4FixtureLightingGate.evaluate(false, 300L, 300L));
    }

    private static V4FixtureGeometryBuilder.Diagnostics structureReadyDiagnostics() {
        V4FixtureGeometryBuilder.Diagnostics diagnostics =
                new V4FixtureGeometryBuilder.Diagnostics();
        diagnostics.geometryChunksRequired = 2;
        diagnostics.geometryChunksReady = 2;
        diagnostics.geometryMutationAttempted = true;
        diagnostics.geometryMutationSucceeded = true;
        diagnostics.fixtureLightBlocksPlaced = 3;
        diagnostics.fixtureLightBlocksVerified = 3;
        diagnostics.geometryStructureVerified = true;
        return diagnostics;
    }
}
