package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploreIdleThresholdPolicyTest {

    @Test
    void opinionOffPreservesBase() {
        assertEquals(600, ExploreIdleThresholdPolicy.effectiveIdleTicks(600, 80f, false));
    }

    @Test
    void neutralBoredomPreservesBase() {
        assertEquals(600, ExploreIdleThresholdPolicy.effectiveIdleTicks(600, 0f, true));
    }

    @Test
    void highBoredomLowersThreshold() {
        int high = ExploreIdleThresholdPolicy.effectiveIdleTicks(600, 100f, true);
        int low = ExploreIdleThresholdPolicy.effectiveIdleTicks(600, 0f, true);
        assertEquals(375, high);
        assertEquals(600, low);
        assertTrue(high < low);
    }

    @Test
    void respectsAbsoluteFloor() {
        assertEquals(
                ExploreIdleThresholdPolicy.ABSOLUTE_MIN_TICKS,
                ExploreIdleThresholdPolicy.effectiveIdleTicks(80, 100f, true));
    }
}
