package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpmEntityOpinionBridgeTest {

    @BeforeEach
    void setUp() {
        OpinionFeatureGate.testOverride = true;
    }

    @AfterEach
    void tearDown() {
        OpinionFeatureGate.testOverride = null;
    }

    @Test
    void mapSpmFeelingCentersNeutralAtZero() {
        assertEquals(0f, SpmEntityOpinionBridge.mapSpmFeelingToOpinionScale(5f), 0.001f);
    }

    @Test
    void mapSpmFeelingPositiveAboveNeutral() {
        assertTrue(SpmEntityOpinionBridge.mapSpmFeelingToOpinionScale(7f) > 0f);
    }

    @Test
    void utilitySupplementFromChannelsBlendsLearnedAffinity() {
        float supplement = SpmEntityOpinionBridge.utilitySupplementFromChannels(0f, 100f);

        assertEquals(
                UtilityNormalizer.channel(100f) * 0.25f * SpmEntityOpinionBridge.UTILITY_SUPPLEMENT_MAX,
                supplement,
                0.001f);
    }

    @Test
    void mutualAboveNeutralRequiresBothFeelings() {
        assertTrue(SpmEntityOpinionBridge.mutualAboveNeutral(6f, 6.1f, 5f));
        assertFalse(SpmEntityOpinionBridge.mutualAboveNeutral(4f, 6f, 5f));
        assertFalse(SpmEntityOpinionBridge.mutualAboveNeutral(null, 6f, 5f));
    }
}
