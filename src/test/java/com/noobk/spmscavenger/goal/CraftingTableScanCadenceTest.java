package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Crafting-table scans share the phased cadence pattern from PERF-1/2. */
class CraftingTableScanCadenceTest {

    private static final int TABLE_SCAN_INTERVAL = 40;
    private static final int CRAFT_TORCHES_SALT = 71;
    private static final int SMELT_SALT = 72;

    @Test
    void craftAndSmeltTableScansUseDistinctPhaseSalts() {
        int craft = PhasedScanClock.phaseFor(42, TABLE_SCAN_INTERVAL, CRAFT_TORCHES_SALT);
        int smelt = PhasedScanClock.phaseFor(42, TABLE_SCAN_INTERVAL, SMELT_SALT);
        int gather = PhasedScanClock.phaseFor(42, 60, 61);
        assertNotEquals(craft, smelt);
        assertNotEquals(craft, gather);
    }
}
