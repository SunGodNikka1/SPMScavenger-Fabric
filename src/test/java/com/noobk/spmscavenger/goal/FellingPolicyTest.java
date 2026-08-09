package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FellingPolicyTest {

    @Test
    void approvedTreeContinuesAcrossSoftCraftingBoundary() {
        assertTrue(FellingPolicy.mayContinueGoal(
                true, true, false),
                "a craftable first log must not cancel the approved trunk");
    }

    @Test
    void ordinaryGatheringStopsWhenAcquisitionIsNoLongerNeeded() {
        assertFalse(FellingPolicy.mayContinueGoal(
                true, false, false));
    }

    @Test
    void hardInterruptionAlwaysStopsAnApprovedTree() {
        assertFalse(FellingPolicy.mayContinueGoal(
                false, true, true));
    }

    @Test
    void fellingLimitIsExactlyTwelveLogs() {
        int felled = 1;
        while (FellingPolicy.mayTakeNextLog(true, true, felled, 12)) {
            felled++;
        }
        assertEquals(12, felled);
        assertFalse(FellingPolicy.mayTakeNextLog(true, true, felled, 12));
    }

    @Test
    void coalOrAEndedTrunkCannotOpenAFellingSession() {
        assertFalse(FellingPolicy.mayTakeNextLog(false, true, 0, 12));
        assertFalse(FellingPolicy.mayTakeNextLog(true, false, 1, 12));
    }
}
