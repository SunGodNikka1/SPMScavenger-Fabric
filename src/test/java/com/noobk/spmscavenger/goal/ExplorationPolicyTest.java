package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationPolicyTest {

    @Test
    void onlyConfirmedAbsenceOfAStayAnchorPermitsExploration() {
        assertTrue(ExplorationPolicy.allowsExpedition(PlayerMobs.StayAnchorState.ABSENT));
        assertFalse(ExplorationPolicy.allowsExpedition(PlayerMobs.StayAnchorState.PRESENT));
        assertFalse(ExplorationPolicy.allowsExpedition(PlayerMobs.StayAnchorState.UNAVAILABLE));
    }

    @Test
    void navigationDoneUsesGraceBeforeFailing() {
        assertFalse(ExplorationPolicy.navigationFailed(true, 0, 0, 20, 100));
        assertFalse(ExplorationPolicy.navigationFailed(true, 19, 19, 20, 100));
        assertTrue(ExplorationPolicy.navigationFailed(true, 20, 20, 20, 100));
    }

    @Test
    void noProgressTimeoutStillBoundsAnActivePath() {
        assertFalse(ExplorationPolicy.navigationFailed(false, 0, 99, 20, 100));
        assertTrue(ExplorationPolicy.navigationFailed(false, 0, 100, 20, 100));
    }

    @Test
    void lateralVariationDoesNotDestroyForwardProgress() {
        double headingX = Math.cos(Math.toRadians(35.0));
        double headingZ = Math.sin(Math.toRadians(35.0));
        double x1 = ExplorationPolicy.projectedX(10.0, headingX, headingZ, 24.0, 8.0);
        double z1 = ExplorationPolicy.projectedZ(-5.0, headingX, headingZ, 24.0, 8.0);
        double x2 = ExplorationPolicy.projectedX(10.0, headingX, headingZ, 60.0, -10.0);
        double z2 = ExplorationPolicy.projectedZ(-5.0, headingX, headingZ, 60.0, -10.0);

        assertEquals(24.0, ExplorationPolicy.forwardProgress(
                10.0, -5.0, headingX, headingZ, x1, z1), 0.0001);
        assertEquals(60.0, ExplorationPolicy.forwardProgress(
                10.0, -5.0, headingX, headingZ, x2, z2), 0.0001);
    }

    @Test
    void forwardDisplacementSkipsButSidewaysDisplacementRejoins() {
        assertEquals(ExplorationPolicy.ResumeAction.SKIP_CURRENT,
                ExplorationPolicy.resumeAction(50.0, 40.0, 2.0, 8.0, 32.0));
        assertEquals(ExplorationPolicy.ResumeAction.REJOIN_HEADING,
                ExplorationPolicy.resumeAction(30.0, 40.0, 35.0, 8.0, 32.0));
        assertEquals(ExplorationPolicy.ResumeAction.KEEP_CURRENT,
                ExplorationPolicy.resumeAction(30.0, 40.0, 10.0, 8.0, 32.0));
    }

    @Test
    void oneHopNeverExceedsWhatThePathfinderWillExpand() {
        // A PlayerMob has FOLLOW_RANGE 32; vanilla A* stops expanding at that distance from the
        // start, so a single request must stay well inside it to survive terrain detours.
        assertEquals(16.0, ExplorationPolicy.maxPathStep(32.0), 0.0001);
        assertEquals(8.0, ExplorationPolicy.maxPathStep(16.0), 0.0001);
        assertEquals(8.0, ExplorationPolicy.maxPathStep(4.0), 0.0001);
        assertEquals(24.0, ExplorationPolicy.maxPathStep(96.0), 0.0001);
    }

    @Test
    void aDistantWaypointIsWalkedInHopsButANearOneIsTakenDirectly() {
        double maxStep = ExplorationPolicy.maxPathStep(32.0);

        // 48 blocks east: the hop lands on the line, exactly maxStep away.
        assertEquals(16.0, ExplorationPolicy.stepCoordinate(0.0, 48.0, 48.0, maxStep), 0.0001);
        assertEquals(0.0, ExplorationPolicy.stepCoordinate(0.0, 0.0, 48.0, maxStep), 0.0001);

        // Already within reach: no intermediate target at all.
        assertEquals(12.0, ExplorationPolicy.stepCoordinate(0.0, 12.0, 12.0, maxStep), 0.0001);
    }

    @Test
    void hopInterpolationHoldsOnADiagonalAndThroughNegativeCoordinates() {
        double distance = Math.sqrt(30.0 * 30.0 + 40.0 * 40.0);
        double x = ExplorationPolicy.stepCoordinate(-10.0, 20.0, distance, 25.0);
        double z = ExplorationPolicy.stepCoordinate(-5.0, 35.0, distance, 25.0);

        double dx = x - -10.0;
        double dz = z - -5.0;
        assertEquals(25.0, Math.sqrt(dx * dx + dz * dz), 0.0001);
    }

    @Test
    void aDegenerateDistanceCannotDivideByZero() {
        assertEquals(5.0, ExplorationPolicy.stepCoordinate(5.0, 5.0, 0.0, 16.0), 0.0001);
    }

    @Test
    void neutralRegardIsNotFriendshipAndBothSidesMustAgree() {
        float neutral = 5.0f;
        // SPM starts every pair at exactly neutral, so neutral must not qualify or the choice of
        // who to travel with would mean nothing.
        assertFalse(ExplorationPolicy.travelsTogether(5.0f, 5.0f, neutral));
        assertFalse(ExplorationPolicy.travelsTogether(7.0f, 5.0f, neutral));
        assertFalse(ExplorationPolicy.travelsTogether(5.0f, 7.0f, neutral));
        assertTrue(ExplorationPolicy.travelsTogether(5.1f, 5.1f, neutral));
    }

    @Test
    void anUnreadableFeelingIsNeverTreatedAsFriendship() {
        assertFalse(ExplorationPolicy.travelsTogether(null, 9.0f, 5.0f));
        assertFalse(ExplorationPolicy.travelsTogether(9.0f, null, 5.0f));
        assertFalse(ExplorationPolicy.travelsTogether(null, null, 5.0f));
    }

    @Test
    void companionsWalkBesideTheLeaderOnAlternatingSides() {
        assertTrue(ExplorationPolicy.companionLateralOffset(0) > 0.0);
        assertTrue(ExplorationPolicy.companionLateralOffset(1) < 0.0);
        assertEquals(-ExplorationPolicy.companionLateralOffset(0),
                ExplorationPolicy.companionLateralOffset(1), 0.0001);
        assertNotEquals(ExplorationPolicy.companionLateralOffset(0),
                ExplorationPolicy.companionLateralOffset(2));
        for (int slot = 0; slot < 8; slot++) {
            assertTrue(Math.abs(ExplorationPolicy.companionLateralOffset(slot)) <= 6.0);
        }
    }

    @Test
    void anInterruptedShortMoveIsNotACompletedLocalTrip() {
        assertTrue(ExplorationPolicy.meaningfulLocalTrip(16.0, 4.0));
        assertTrue(!ExplorationPolicy.meaningfulLocalTrip(15.99, 4.0));
    }

    @Test
    void coarseRegionsHandleNegativeCoordinatesAndStayDistinct() {
        long negative = ExplorationPolicy.regionKey(-1, -1, 4);
        long origin = ExplorationPolicy.regionKey(0, 0, 4);
        long sameOriginRegion = ExplorationPolicy.regionKey(63, 63, 4);
        assertNotEquals(negative, origin);
        assertEquals(origin, sameOriginRegion);
    }

    @Test
    void headingSectorsWrapWithoutProducingAnInvalidIndex() {
        int east = ExplorationPolicy.headingSector(1.0, 0.0, 12);
        int almostEast = ExplorationPolicy.headingSector(1.0, -0.0001, 12);
        assertTrue(east >= 0 && east < 12);
        assertTrue(almostEast >= 0 && almostEast < 12);
    }

    @Test
    void simulationFrontierIsNotTreatedAsPathFailure() {
        assertEquals(ExplorationPolicy.FailureAction.ABANDON_SIMULATION_FRONTIER,
                ExplorationPolicy.failureAction(true, 0, 0, 3, 6, false, true));
        assertEquals(ExplorationPolicy.FailureAction.RETRY_WAYPOINT,
                ExplorationPolicy.failureAction(false, 1, 1, 3, 6, false, true));
    }

    @Test
    void waypointRetriesAreBoundedWithoutDiscardingTheRemainingRoute() {
        assertEquals(ExplorationPolicy.FailureAction.SKIP_WAYPOINT,
                ExplorationPolicy.failureAction(false, 3, 3, 3, 6, false, true));
        assertEquals(ExplorationPolicy.FailureAction.ABANDON_PATH,
                ExplorationPolicy.failureAction(false, 3, 3, 3, 6, false, false));
        assertEquals(ExplorationPolicy.FailureAction.DROP_REJOIN,
                ExplorationPolicy.failureAction(false, 3, 3, 3, 6, true, false));
    }
}
