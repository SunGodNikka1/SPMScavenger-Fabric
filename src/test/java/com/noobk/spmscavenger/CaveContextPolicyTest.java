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
    void openRavineDetectedViaLocalRim() {
        // Column surface ≈ feet (open sky); rim around is high.
        assertFalse(CaveContextPolicy.isCaveLike(25, 25));
        assertTrue(CaveContextPolicy.isCaveOrRavineLike(25, 25, 68));
    }

    @Test
    void localRimUsesUpperMedian() {
        assertEquals(70, CaveContextPolicy.localRimHeight(68, 70, 67, 69, 71, 66, 68, 70));
    }

    @Test
    void landingModesCombineDescentAndCave() {
        assertEquals(
                CaveContextPolicy.LandingMode.DESCENT_IN_CAVE,
                CaveContextPolicy.resolveLandingMode(true, true));
        assertEquals(
                CaveContextPolicy.LandingMode.DESCENT,
                CaveContextPolicy.resolveLandingMode(true, false));
        assertEquals(
                CaveContextPolicy.LandingMode.CAVE_CONTINUATION,
                CaveContextPolicy.resolveLandingMode(false, true));
        assertEquals(
                CaveContextPolicy.LandingMode.NORMAL,
                CaveContextPolicy.resolveLandingMode(false, false));
    }

    @Test
    void descentInCavePrefersDeepUnderSurfaceOverSurfaceTop() {
        int mobY = 40;
        int rim = 72;
        int caveFloor = CaveContextPolicy.landingPreferenceKey(
                CaveContextPolicy.LandingMode.DESCENT_IN_CAVE, 32, mobY, rim);
        int surfaceTop = CaveContextPolicy.landingPreferenceKey(
                CaveContextPolicy.LandingMode.DESCENT_IN_CAVE, 72, mobY, rim);
        assertTrue(caveFloor < surfaceTop);
    }

    @Test
    void caveOpportunityUsesCandidateNotOnlyMob() {
        // Mob on surface; ore deep under its column roof.
        assertTrue(CaveContextPolicy.caveOpportunity(false, true, false));
        // Mob in ravine; ore still under rim even if column surface flat.
        assertTrue(CaveContextPolicy.caveOpportunity(true, false, true));
        // Neither.
        assertFalse(CaveContextPolicy.caveOpportunity(false, false, false));
    }

    @Test
    void oreBonusOnlyForOreWithOpportunity() {
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
        assertTrue(CaveContextPolicy.landingPreferenceKey(
                        CaveContextPolicy.LandingMode.CAVE_CONTINUATION, 35, mobY, surface)
                < CaveContextPolicy.landingPreferenceKey(
                        CaveContextPolicy.LandingMode.CAVE_CONTINUATION, 72, mobY, surface));
    }
}
