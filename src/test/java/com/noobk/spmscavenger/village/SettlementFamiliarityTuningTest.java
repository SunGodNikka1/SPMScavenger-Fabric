package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** VR-T1.5b — gen-1 familiarity accumulation tuning (User rebalance 2026-08-15). */
class SettlementFamiliarityTuningTest {

    @Test
    void mustHappen_discoverAndFiveMinutesPresenceReachesMedium() {
        SettlementRelationship relationship = SettlementRelationship.empty();
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 100L);

        // ~5 minutes at +5 / 200 ticks (10 seconds).
        for (int i = 0; i < 30; i++) {
            relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, 200L + i);
        }

        assertEquals(200, relationship.familiarityScore());
        assertEquals(AttachmentBand.MEDIUM, relationship.attachmentBand());
        assertEquals(150, relationship.presenceFamiliarity());
    }

    @Test
    void mustNotHappen_passivePresenceAloneReachesHigh() {
        SettlementRelationship relationship = SettlementRelationship.empty();
        for (int i = 0; i < 200; i++) {
            relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, i);
        }

        assertEquals(SettlementTuning.PRESENCE_FAMILIARITY_CAP, relationship.familiarityScore());
        assertEquals(SettlementTuning.PRESENCE_FAMILIARITY_CAP, relationship.presenceFamiliarity());
        assertEquals(AttachmentBand.MEDIUM, relationship.attachmentBand());
        assertFalse(relationship.attachmentBand() == AttachmentBand.HIGH);
    }

    @Test
    void mustHappen_returnsAndSocialCanReachHighBeyondPresenceCap() {
        SettlementRelationship relationship = SettlementRelationship.empty();
        for (int i = 0; i < 50; i++) {
            relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, i);
        }
        assertEquals(250, relationship.presenceFamiliarity());

        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 500L);
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 1_000L);
        relationship.recordSocialEpisode(1_100L);
        relationship.recordSocialEpisode(1_200L);
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 1_500L);
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 2_000L);
        relationship.recordSocialEpisode(2_100L);
        relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, 2_500L);

        assertTrue(relationship.familiarityScore() >= SettlementTuning.HIGH_BAND_MIN);
        assertEquals(AttachmentBand.HIGH, relationship.attachmentBand());
        assertEquals(250, relationship.presenceFamiliarity(), "presence channel stays capped");
    }

    @Test
    void mustHappen_presenceCapBlocksFurtherPassiveTicks() {
        SettlementRelationship relationship = SettlementRelationship.empty();
        for (int i = 0; i < SettlementTuning.PRESENCE_FAMILIARITY_CAP
                / SettlementTuning.PRESENCE_FAMILIARITY_BUMP; i++) {
            relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, i);
        }
        int scoreAtCap = relationship.familiarityScore();
        relationship.bumpPresenceFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, 9_999L);
        assertEquals(scoreAtCap, relationship.familiarityScore());
    }
}
