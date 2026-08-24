package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class V3CampaignScenarioTest {

    @Test
    void allowlistContainsExactlyTheThirteenLockedPresets() {
        assertEquals(13, V3CampaignScenario.values().length);
        assertTrue(V3CampaignScenario.byId("mandatory_blocks_village_work").isPresent());
        assertTrue(V3CampaignScenario.byId("mandatory_ownership_witness").isPresent());
        assertTrue(V3CampaignScenario.byId("crop_multi_cycle").isPresent());
        assertFalse(V3CampaignScenario.byId("anything_else").isPresent());
    }

    @Test
    void onlyUnknownStorageSkipsSettlementGate0() {
        for (V3CampaignScenario scenario : V3CampaignScenario.values()) {
            assertEquals(!scenario.id().equals("storage_unknown_deny"), scenario.requiresGate0(),
                    scenario.id());
        }
    }

    @Test
    void exactFixedWindowsAndTransitionBoundsMatchMatrixContract() {
        assertEquals(1200, V3CampaignScenario.POPULATION_FOOD_DEFICIT.fixedWindowTicks());
        assertEquals(800, V3CampaignScenario.STORAGE_PUBLIC_DENY.fixedWindowTicks());
        assertEquals(800, V3CampaignScenario.STORAGE_UNKNOWN_DENY.fixedWindowTicks());
        assertEquals(800, V3CampaignScenario.STORAGE_GRANTED_PERMIT.fixedWindowTicks());
        assertEquals(1000, V3CampaignScenario.MANDATORY_BLOCKS_VILLAGE_WORK.fixedWindowTicks());
        assertEquals(1000, V3CampaignScenario.MANDATORY_OWNERSHIP_WITNESS.fixedWindowTicks());
        assertEquals(120, V3CampaignScenario.CROP_INTERRUPT_COMBAT.triggerDelayTicks());
        assertEquals(600, V3CampaignScenario.CROP_INTERRUPT_COMBAT.stabilizationTicks());
        assertEquals(4000, V3CampaignScenario.CROP_MULTI_CYCLE.maxWindowTicks());
    }
}
