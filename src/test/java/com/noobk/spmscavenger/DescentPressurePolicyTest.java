package com.noobk.spmscavenger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescentPressurePolicyTest {

    @Test
    void surfaceProgressionWithoutSightingRequestsDescent() {
        assertTrue(DescentPressurePolicy.wantsDescentExplore(3, false, false));
    }

    @Test
    void deepLocalBandDoesNotRequestDescent() {
        assertFalse(DescentPressurePolicy.wantsDescentExplore(3, true, false));
    }

    @Test
    void noProgressionDoesNotRequestDescent() {
        assertFalse(DescentPressurePolicy.wantsDescentExplore(0, false, false));
    }

    @Test
    void legitimateSightingDefersDescentExplore() {
        assertFalse(DescentPressurePolicy.wantsDescentExplore(3, false, true));
    }

    @Test
    void landingPreferencePrefersBelowMob() {
        int mobY = 64;
        assertTrue(DescentPressurePolicy.landingPreferenceKey(50, mobY)
                < DescentPressurePolicy.landingPreferenceKey(70, mobY));
        assertEquals(0, DescentPressurePolicy.landingPreferenceKey(50, mobY) / 1000);
        assertEquals(1, DescentPressurePolicy.landingPreferenceKey(70, mobY) / 1000);
    }
}
