package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherApproachPolicyTest {

    @Test
    void initialTreeTargetMustBeTheBottomLog() {
        assertTrue(GatherApproachPolicy.isInitialTreeLog(true, false));
        assertFalse(GatherApproachPolicy.isInitialTreeLog(true, true));
        assertFalse(GatherApproachPolicy.isInitialTreeLog(false, false));
    }

    @Test
    void meaningfulDistanceImprovementResetsAStall() {
        assertTrue(GatherApproachPolicy.madeProgress(16.0, 15.0, 0.25));
        assertFalse(GatherApproachPolicy.madeProgress(16.0, 15.9, 0.25));
    }

    @Test
    void foliageMayDefineARecoveryDestinationButSolidBlocksMayNot() {
        assertTrue(GatherApproachPolicy.isApproachCellAllowed(false, true, true));
        assertFalse(GatherApproachPolicy.isApproachCellAllowed(false, true, false));
        assertFalse(GatherApproachPolicy.isApproachCellAllowed(false, false, true));
        assertTrue(GatherApproachPolicy.isApproachCellAllowed(true, false, false));
    }

    @Test
    void directLeafCanBeClearedOnlyAfterTheStallThreshold() {
        assertFalse(mayClear(true, true, true, true, true, true, 0, 19));
        assertTrue(mayClear(true, true, true, true, true, true, 0, 20));
    }

    @Test
    void threeLeafLimitIsExact() {
        assertTrue(mayClear(true, true, true, true, true, true, 2, 20));
        assertFalse(mayClear(true, true, true, true, true, true, 3, 20));
    }

    @Test
    void recoveryRejectsOreNonLeavesBuildsAndDisabledWorldDamage() {
        assertFalse(mayClear(false, true, true, true, true, true, 0, 20));
        assertFalse(mayClear(true, false, true, true, true, true, 0, 20));
        assertFalse(mayClear(true, true, false, true, true, true, 0, 20));
        assertFalse(mayClear(true, true, true, false, true, true, 0, 20));
        assertFalse(mayClear(true, true, true, true, false, true, 0, 20));
        assertFalse(mayClear(true, true, true, true, true, false, 0, 20));
    }

    private static boolean mayClear(
            boolean tree,
            boolean enabled,
            boolean griefing,
            boolean leaf,
            boolean nearby,
            boolean buildClear,
            int cleared,
            int stalled) {
        return GatherApproachPolicy.mayClearLeaf(
                tree, enabled, griefing, leaf, nearby, buildClear, cleared, 3, stalled, 20);
    }
}
