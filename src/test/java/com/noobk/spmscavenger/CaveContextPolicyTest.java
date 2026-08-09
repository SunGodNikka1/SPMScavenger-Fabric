package com.noobk.spmscavenger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveContextPolicyTest {

    @Test
    void deepUnderSurfaceIsCaveLike() {
        assertTrue(CaveContextPolicy.isCaveLike(10, 70));
        assertFalse(CaveContextPolicy.isCaveLike(68, 70));
        assertFalse(CaveContextPolicy.isCaveLike(70, 70));
    }

    @Test
    void oreBonusOnlyInCaveForOre() {
        assertEquals(0, CaveContextPolicy.orePriorityBonus(
                false, GatherIntentPolicy.Resource.RAW_IRON));
        assertEquals(0, CaveContextPolicy.orePriorityBonus(
                true, GatherIntentPolicy.Resource.LOGS));
        assertEquals(CaveContextPolicy.CAVE_ORE_PRIORITY_BONUS,
                CaveContextPolicy.orePriorityBonus(true, GatherIntentPolicy.Resource.COAL));
    }

    @Test
    void landingPrefersStayingUnderSurface() {
        int mobY = 40;
        int surface = 72;
        assertTrue(CaveContextPolicy.landingPreferenceKey(35, mobY, surface)
                < CaveContextPolicy.landingPreferenceKey(72, mobY, surface));
    }
}
