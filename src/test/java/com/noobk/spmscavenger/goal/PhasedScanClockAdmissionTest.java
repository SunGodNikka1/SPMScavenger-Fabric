package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhasedScanClockAdmissionTest {

    @Test
    void isDueDoesNotConsumeScanSlot() {
        PhasedScanClock clock = new PhasedScanClock(7, RestCampfireFeasibility.SCAN_INTERVAL, 37);
        long tick = 0L;
        while (!clock.isDue(tick)) {
            tick++;
        }
        assertTrue(clock.isDue(tick));
        assertFalse(clock.claim(tick - 1));
        assertTrue(clock.claim(tick));
    }
}
