package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** VR-T1.5b — split visit vs presence timing; block standing-still visit farm. */
class SettlementVisitPresenceSemanticsTest {

    @Test
    void mustHappen_presenceAtCapStillAdvancesLastPresenceTick() {
        SettlementRelationship relationship = new SettlementRelationship(
                250, 1_000L, 0, 250, 5_000L, 0L);

        relationship.recordPresenceHeartbeat(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, 5_200L);

        assertEquals(250, relationship.familiarityScore());
        assertEquals(250, relationship.presenceFamiliarity());
        assertEquals(5_200L, relationship.lastPresenceTick());
        assertEquals(1_000L, relationship.lastVisitTick());
    }

    @Test
    void mustNotHappen_continuousResidencyQualifiesForReentryVisit() {
        SettlementRelationship relationship = new SettlementRelationship(
                250, 5_000L, 0, 250, 5_200L, 0L);

        assertFalse(relationship.qualifiesForReentryVisit());
    }

    @Test
    void mustHappen_departureEnablesReentryVisit() {
        SettlementRelationship relationship = new SettlementRelationship(
                250, 5_000L, 0, 250, 5_200L, 0L);

        relationship.noteOutsideBounds(5_300L);

        assertTrue(relationship.qualifiesForReentryVisit());
    }

    @Test
    void mustNotHappen_cappedPresenceThenReentryVisitAloneDoesNotFarmHigh() {
        SettlementRelationship relationship = new SettlementRelationship(
                250, 5_000L, 0, 250, 5_200L, 0L);

        relationship.recordPresenceHeartbeat(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, 5_400L);
        // Continuous residency: routine re-observation must not grant +50 visit.
        assertFalse(relationship.qualifiesForReentryVisit());
        assertEquals(250, relationship.familiarityScore());
    }

    @Test
    void mustHappen_reentryVisitAddsBeyondPresenceCap() {
        SettlementRelationship relationship = new SettlementRelationship(
                250, 5_000L, 0, 250, 5_200L, 0L);
        relationship.noteOutsideBounds(5_300L);

        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 5_400L);

        assertEquals(300, relationship.familiarityScore());
        assertEquals(250, relationship.presenceFamiliarity());
    }

    @Test
    void mustHappen_presenceAndOutsideTicksRoundTripThroughNbt() {
        SettlementRelationship relationship = new SettlementRelationship(
                120, 90L, 1, 100, 500L, 600L);

        SettlementRelationship reloaded = SettlementRelationship.load(relationship.save());

        assertEquals(500L, reloaded.lastPresenceTick());
        assertEquals(600L, reloaded.lastOutsideTick());
    }
}
