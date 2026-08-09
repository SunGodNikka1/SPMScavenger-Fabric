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

    // ---- MI-6G: CaveContextSnapshot classifier ----

    private static CaveContextPolicy.CaveContextSnapshot snap(
            int feetY, int columnSurfaceY, int localRimY, boolean skyVisible) {
        return new CaveContextPolicy.CaveContextSnapshot(
                feetY, columnSurfaceY, localRimY, skyVisible);
    }

    @Test
    void mustNotHappen_aCellarUnderAHouseClassifiesAsCave() {
        // The recorded weird behaviour: feet Y60, roof Y70, natural ground around it also Y60.
        // enclosureDepth 10 passes the raw isCaveLike test; localRimDepth 0 does not.
        CaveContextPolicy.CaveContextSnapshot cellar = snap(60, 70, 60, false);

        assertTrue(CaveContextPolicy.isCaveOrRavineLike(60, 70, 60),
                "the raw geometric test still reports true - that is why it is not the classifier");
        assertEquals(CaveContextPolicy.SpaceKind.ENCLOSED_STRUCTURE,
                CaveContextPolicy.classify(cellar));
        assertFalse(CaveContextPolicy.isSubterranean(cellar),
                "a basement must not unlock underground ore reasoning");
    }

    @Test
    void mustHappen_aRealCaveClassifiesAsCave() {
        CaveContextPolicy.CaveContextSnapshot cave = snap(32, 70, 70, false);
        assertEquals(CaveContextPolicy.SpaceKind.CAVE, CaveContextPolicy.classify(cave));
        assertTrue(CaveContextPolicy.isSubterranean(cave));
        assertEquals(38, cave.localRimDepth());
    }

    @Test
    void mustHappen_anOpenRavineIsSubterraneanButNotACave() {
        // Column open to the sky, surrounding rim far above: below terrain, not enclosed.
        CaveContextPolicy.CaveContextSnapshot ravine = snap(40, 40, 68, true);
        assertEquals(CaveContextPolicy.SpaceKind.RAVINE, CaveContextPolicy.classify(ravine));
        assertTrue(ravine.belowLocalTerrain());
        assertFalse(ravine.enclosed());
        assertTrue(CaveContextPolicy.isSubterranean(ravine));
    }

    @Test
    void mustHappen_openGroundIsSurface() {
        CaveContextPolicy.CaveContextSnapshot surface = snap(70, 70, 70, true);
        assertEquals(CaveContextPolicy.SpaceKind.SURFACE, CaveContextPolicy.classify(surface));
        assertFalse(CaveContextPolicy.isSubterranean(surface));
    }

    @Test
    void mustNotHappen_anUnsampledRimIsTreatedAsDepth() {
        // No rim samples must read as level ground, never as an accidental cave.
        CaveContextPolicy.CaveContextSnapshot unsampled =
                snap(60, 70, Integer.MIN_VALUE, false);
        assertEquals(0, unsampled.localRimDepth());
        assertEquals(CaveContextPolicy.SpaceKind.ENCLOSED_STRUCTURE,
                CaveContextPolicy.classify(unsampled));
    }

    @Test
    void mustHappen_theBoundaryIsTheDocumentedDepth() {
        int min = CaveContextPolicy.MIN_DEPTH_BELOW_SURFACE;
        assertFalse(snap(60, 60 + min, 60 + min - 1, false).belowLocalTerrain());
        assertTrue(snap(60, 60 + min, 60 + min, false).belowLocalTerrain());
    }
}
