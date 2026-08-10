package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-2 — gather global scans use entity-phased cadence without changing scan semantics. */
class GatherScanCadenceTest {

    private static final int SCAN_INTERVAL = 60;
    private static final int SCAN_PHASE_SALT = 61;

    @Test
    void gatherUsesDistinctPhaseSaltFromShelterAndCampfire() {
        int gather = PhasedScanClock.phaseFor(42, SCAN_INTERVAL, SCAN_PHASE_SALT);
        int shelter = PhasedScanClock.phaseFor(42, SCAN_INTERVAL, 11);
        int campfire = PhasedScanClock.phaseFor(42, SCAN_INTERVAL, 37);
        assertNotEquals(gather, shelter);
        assertNotEquals(gather, campfire);
    }

    @Test
    void failedScanReschedulesToNextPhaseRatherThanImmediateRetry() {
        PhasedScanClock clock = new PhasedScanClock(4, SCAN_INTERVAL, SCAN_PHASE_SALT);
        int phase = PhasedScanClock.phaseFor(4, SCAN_INTERVAL, SCAN_PHASE_SALT);
        assertTrue(clock.claim(phase));
        clock.resetAfter(phase);
        assertFalse(clock.claim(phase + 1), "reset must defer until the next assigned slot");
    }
}
