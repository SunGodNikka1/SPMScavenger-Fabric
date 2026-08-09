package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhasedScanClockTest {

    @Test
    void sequentialEntityIdsFillEveryPhaseExactlyOnce() {
        Set<Integer> phases = new HashSet<>();
        for (int entityId = 1; entityId <= 60; entityId++) {
            phases.add(PhasedScanClock.phaseFor(entityId, 60, 17));
        }
        assertEquals(60, phases.size());
    }

    @Test
    void differentGoalSaltsSeparateScanTypesForOneMob() {
        int gather = PhasedScanClock.phaseFor(42, 60, 0);
        int shelter = PhasedScanClock.phaseFor(42, 60, 19);
        assertFalse(gather == shelter);
    }

    @Test
    void aPollAfterTheExactPhaseStillClaimsTheTurnOnce() {
        PhasedScanClock clock = new PhasedScanClock(2, 10, 0); // phase 2
        assertFalse(clock.claim(0));
        assertTrue(clock.claim(3), "tick 2 was skipped by the caller");
        assertFalse(clock.claim(3), "one late poll must not claim twice");
        assertFalse(clock.claim(11));
        assertTrue(clock.claim(12));
    }

    @Test
    void resetDefersUntilTheNextAssignedPhase() {
        PhasedScanClock clock = new PhasedScanClock(4, 10, 0);
        assertTrue(clock.claim(4));
        clock.resetAfter(7);
        assertFalse(clock.claim(13));
        assertTrue(clock.claim(14));
    }

    @Test
    void invalidIntervalsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PhasedScanClock(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PhasedScanClock.phaseFor(1, -1, 0));
    }
}
