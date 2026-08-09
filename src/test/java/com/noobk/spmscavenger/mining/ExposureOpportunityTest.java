package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MIW-TS2 — Exposure Opportunity Handoff.
 *
 * <p>Tunnel Search never asks "is there diamond nearby". It knows only which cells it physically
 * opened, and offers that boundary to a downstream consumer. These tests hold the five rules locked
 * before implementation: the probe precedes the consumer's global admission, it still uses real
 * demand, it is consumed only when it actually executes, the opportunity is shared and
 * session-bound, and a started acquisition finishes its vein before the producer reacquires.
 */
class ExposureOpportunityTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("exposure".getBytes());
    private static final BlockPos ORIGIN = new BlockPos(0, 12, 0);
    private static final long STARTED = 5_000L;

    private static MiningProject tunnelProject() {
        return MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, ORIGIN, Direction.EAST,
                MiningBudget.controlledDescentDefaults(), STARTED);
    }

    /** Cells a 1x2 step opens: forward head and forward feet. */
    private static List<BlockPos> openedCells() {
        return HorizontalStepPlanner.planStep(ORIGIN, Direction.EAST).requiredBreaks();
    }

    private static MiningProjectSavedData storeWithOffer(long now) {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningProject project = tunnelProject();
        store.putProject(MOB, project);
        store.offerExposure(MOB, project, openedCells(), now);
        return store;
    }

    // ---- session binding ----

    @Test
    void mustNotHappen_aStaleOfferIsConsumedByTheNextTunnel() {
        MiningProjectSavedData store = storeWithOffer(STARTED);

        // Same mode, same origin, different session - a restart, not a resume.
        MiningProject restarted = MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, ORIGIN, Direction.EAST,
                MiningBudget.controlledDescentDefaults(), STARTED + 1);

        assertFalse(ExposureOpportunityPolicy.offersProbe(
                        store.exposureOf(MOB).orElseThrow(), restarted, STARTED + 2),
                "an opportunity belongs to the session that cut it, not to the location");
        assertTrue(store.takeExposureProbe(MOB, restarted, STARTED + 2).isEmpty());
    }

    @Test
    void mustNotHappen_anotherModeOffersAnExposure() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningProject descent =
                MiningProject.startControlledDescent(ORIGIN, Direction.EAST, STARTED);
        store.putProject(MOB, descent);
        store.offerExposure(MOB, descent, openedCells(), STARTED);

        assertFalse(ExposureOpportunityPolicy.offersProbe(
                        store.exposureOf(MOB).orElseThrow(), descent, STARTED),
                "only TUNNEL_SEARCH offers cooperative exposure - a staircase is not a handoff");
    }

    @Test
    void mustNotHappen_anOpportunityOutlivesItsProject() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        ExposureOpportunity offer = store.exposureOf(MOB).orElseThrow();

        assertFalse(ExposureOpportunityPolicy.isLive(offer, null, STARTED),
                "no project, no authority to offer anything");
        assertFalse(ExposureOpportunityPolicy.isLive(
                        offer, tunnelProject().complete(MiningProjectEnd.NO_PROGRESS), STARTED),
                "a completed project cannot be served by a consumer acting on its behalf");
    }

    // ---- consume-on-execute ----

    @Test
    void mustHappen_theProbeIsConsumedOnlyWhenItActuallyExecutes() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();

        // A consumer that never gets admitted must not destroy the offer.
        assertTrue(store.exposureOf(MOB).isPresent());
        assertSame(ExposureOpportunity.Phase.OFFERED, store.exposureOf(MOB).orElseThrow().phase());

        // Probe executes, finds nothing: the take itself already released it.
        assertTrue(store.takeExposureProbe(MOB, project, STARTED + 1).isPresent());
        assertTrue(store.exposureOf(MOB).isEmpty(),
                "a probe that ran and found nothing releases the tunnel immediately, with no "
                        + "cleanup call the caller could forget");
    }

    @Test
    void mustNotHappen_anUnprobedOfferSurvivesForever() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();
        long stale = STARTED + ExposureOpportunityPolicy.OFFER_LIFETIME_TICKS + 1;

        assertTrue(store.takeExposureProbe(MOB, project, stale).isEmpty(),
                "the mob has walked away; sending it back to that wall is not cooperation");
    }

    // ---- vein continuation, the anti-ping-pong rule ----

    @Test
    void mustHappen_anAcquisitionHoldsUntilTheVeinIsDone() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();

        ExposureOpportunity taken = store.takeExposureProbe(MOB, project, STARTED + 5)
                .orElseThrow();
        assertTrue(store.beginCooperativeAcquisition(MOB, project, taken, STARTED + 5));
        assertTrue(ExposureOpportunityPolicy.holdsCooperativeSession(
                        store.exposureOf(MOB).orElseThrow(), project, STARTED + 5),
                "the producer must not reacquire between two ores of the same vein");

        // Each take refreshes the idle clock, so a long vein does not time out mid-way.
        long t = STARTED + 5;
        for (int ore = 0; ore < 6; ore++) {
            t += ExposureOpportunityPolicy.VEIN_IDLE_TICKS - 1;
            assertTrue(store.noteCooperativeAcquisition(MOB, project, t));
            assertTrue(ExposureOpportunityPolicy.holdsCooperativeSession(
                            store.exposureOf(MOB).orElseThrow(), project, t),
                    "ore " + ore + ": vein-follow is still productive work");
        }

        long idle = t + ExposureOpportunityPolicy.VEIN_IDLE_TICKS + 1;
        assertFalse(ExposureOpportunityPolicy.holdsCooperativeSession(
                        store.exposureOf(MOB).orElseThrow(), project, idle),
                "but an acquisition that stops producing must release the tunnel");
    }

    @Test
    void mustNotHappen_afreshCutStealsAnActiveAcquisition() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();
        ExposureOpportunity taken = store.takeExposureProbe(MOB, project, STARTED + 5)
                .orElseThrow();
        store.beginCooperativeAcquisition(MOB, project, taken, STARTED + 5);

        store.offerExposure(MOB, project, List.of(new BlockPos(9, 12, 0)), STARTED + 6);

        assertSame(ExposureOpportunity.Phase.ACQUIRING, store.exposureOf(MOB).orElseThrow().phase(),
                "the consumer is mid-vein; overwriting its boundary would strand it");
    }

    // ---- exposure-local, not a radius ----

    @Test
    void mustHappen_onlyTheExcavationBoundaryIsInspectable() {
        ExposureOpportunity offer = ExposureOpportunity.offer(
                tunnelProject(), openedCells(), STARTED);
        BlockPos openedFeet = new BlockPos(1, 12, 0);

        assertTrue(ExposureOpportunityPolicy.isExposureLocal(offer, openedFeet));
        assertTrue(ExposureOpportunityPolicy.isExposureLocal(offer, openedFeet.north()),
                "ore in the side wall the cut revealed");
        assertTrue(ExposureOpportunityPolicy.isExposureLocal(offer, openedFeet.below()),
                "ore in the floor the cut revealed");
        assertFalse(ExposureOpportunityPolicy.isExposureLocal(offer, openedFeet.north(2)),
                "two blocks away is behind a wall - inspecting it would be clairvoyance");
        assertFalse(ExposureOpportunityPolicy.isExposureLocal(offer, ORIGIN.east(6)),
                "a radius would readmit the broad search this path exists to avoid");
    }

    // ---- geometry (D-MIW-TS3) ----

    @Test
    void mustHappen_theCorridorStepIsFlatAndTwoHigh() {
        StairStepPlan plan = HorizontalStepPlanner.planStep(ORIGIN, Direction.EAST);

        assertEquals(new BlockPos(1, 12, 0), plan.nextStandCell(), "a corridor does not descend");
        assertEquals(ORIGIN.getY(), plan.nextStandCell().getY());
        assertEquals(0, plan.resultingDrop());
        assertEquals(2, plan.requiredBreaks().size(), "1x2: head and feet, nothing else");
        assertEquals(new BlockPos(1, 13, 0), plan.requiredBreaks().get(0),
                "headroom first, so a falling-block hazard shows before the mob stands in the gap");
        assertEquals(new BlockPos(1, 12, 0), plan.requiredBreaks().get(1));
    }

    @Test
    void mustNotHappen_theCorridorInheritsStaircaseGeometry() {
        StairStepPlan corridor = HorizontalStepPlanner.planStep(ORIGIN, Direction.EAST);
        StairStepPlan stair = StairStepPlanner.planStep(ORIGIN, Direction.EAST);

        assertEquals(3, stair.requiredBreaks().size());
        assertEquals(1, stair.resultingDrop());
        assertFalse(corridor.nextStandCell().equals(stair.nextStandCell()),
                "sharing the plan type must not mean sharing the drop - a corridor that inherited "
                        + "DROP_TOO_DEEP logic would reject flat ground");
    }

    // ---- TS2-M1: the store enforces the state machine, not just describes it ----

    @Test
    void mustNotHappen_theSameOfferIsProbedTwice() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();

        assertTrue(store.takeExposureProbe(MOB, project, STARTED + 1).isPresent(),
                "first take inspects the boundary");
        assertTrue(store.takeExposureProbe(MOB, project, STARTED + 2).isEmpty(),
                "one probe per exposure event must be a mechanism, not a comment - the previous "
                        + "version returned the same offer every tick until it expired");
    }

    @Test
    void mustNotHappen_anAcquisitionBeginsWithoutASuccessfulTake() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();

        assertFalse(store.beginCooperativeAcquisition(MOB, project, null, STARTED + 1),
                "no probe evidence, no session");
        assertSame(ExposureOpportunity.Phase.OFFERED, store.exposureOf(MOB).orElseThrow().phase(),
                "and the stored offer is untouched by the attempt");
    }

    @Test
    void mustNotHappen_anAcquisitionBeginsWithOldSessionEvidence() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();
        ExposureOpportunity taken = store.takeExposureProbe(MOB, project, STARTED + 1)
                .orElseThrow();

        MiningProject restarted = MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, ORIGIN, Direction.EAST,
                MiningBudget.controlledDescentDefaults(), STARTED + 1);

        assertFalse(store.beginCooperativeAcquisition(MOB, restarted, taken, STARTED + 2),
                "evidence cut by the previous tunnel cannot open a session for the next one");
        assertTrue(store.exposureOf(MOB).isEmpty());
    }

    @Test
    void mustNotHappen_anAcquisitionBeginsFromStaleEvidence() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();
        ExposureOpportunity taken = store.takeExposureProbe(MOB, project, STARTED + 1)
                .orElseThrow();
        long stale = STARTED + ExposureOpportunityPolicy.OFFER_LIFETIME_TICKS + 1;

        assertFalse(store.beginCooperativeAcquisition(MOB, project, taken, stale),
                "a probe held across a long interruption must not reopen a session on arrival");
    }

    @Test
    void mustNotHappen_anOfferedExposureHasItsLifetimeRefreshed() {
        MiningProjectSavedData store = storeWithOffer(STARTED);
        MiningProject project = tunnelProject();

        assertFalse(store.noteCooperativeAcquisition(MOB, project, STARTED + 50),
                "only a live ACQUIRING session may refresh - otherwise the 100-tick freshness "
                        + "bound becomes advisory and an offer never expires");

        long stale = STARTED + ExposureOpportunityPolicy.OFFER_LIFETIME_TICKS + 1;
        assertTrue(store.takeExposureProbe(MOB, project, stale).isEmpty(),
                "so it still expires on schedule");
    }
}
