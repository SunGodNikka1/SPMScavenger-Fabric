package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningPolicyTest {

    @Test
    void correctFastToolFinishesBeforeBareHand() {
        assertEquals(8, MiningPolicy.requiredTicks(2.0F, 8.0F, true));
        assertEquals(200, MiningPolicy.requiredTicks(2.0F, 1.0F, false));
    }

    @Test
    void instantAndUnbreakableBlocksKeepTheirVanillaBoundaries() {
        assertEquals(1, MiningPolicy.requiredTicks(0.0F, 1.0F, true));
        assertEquals(Integer.MAX_VALUE, MiningPolicy.requiredTicks(-1.0F, 100.0F, true));
    }

    @Test
    void crackAnimationIsBoundedFromZeroThroughNine() {
        assertEquals(0, MiningPolicy.crackStage(0, 20));
        assertEquals(5, MiningPolicy.crackStage(10, 20));
        assertEquals(9, MiningPolicy.crackStage(20, 20));
    }

    @Test
    void dropsFollowTheCorrectToolRuleVanillaEnforcesOutsideTheLootTable() {
        // Blocks that do not require a tool always drop.
        assertTrue(MiningPolicy.dropsAllowed(false, false));
        assertTrue(MiningPolicy.dropsAllowed(false, true));
        // Blocks that do require one drop only with it - the case a bare-handed mob would otherwise
        // have been paid for.
        assertFalse(MiningPolicy.dropsAllowed(true, false));
        assertTrue(MiningPolicy.dropsAllowed(true, true));
    }

    @Test
    void theHarvestRuleAgreesWithTheBreakTimingItAlsoDecides() {
        // Same boolean drives the 30x vs 100x divisor, so a harvestable break is never slower.
        int harvestable = MiningPolicy.requiredTicks(3.0F, 8.0F, MiningPolicy.dropsAllowed(true, true));
        int unharvestable = MiningPolicy.requiredTicks(3.0F, 8.0F, MiningPolicy.dropsAllowed(true, false));
        assertTrue(harvestable < unharvestable);
    }
}
