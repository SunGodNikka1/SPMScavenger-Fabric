package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GAO-5B — place opinion biases expedition destination ranking, not EXPLORE activity utility.
 */
class PlaceOpinionRouteRankerTest {

    @AfterEach
    void resetGate() {
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void mustHappen_negativeDestinationLosesToNeutralWhenBaseScoresEqual() {
        OpinionFeatureGate.setTestOverride(true);
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        places.recordOutcome(new ChunkPos(100, 0).toLong(), -80f);

        int base = 50;
        int disliked = base + PlaceOpinionRouteRanker.routeBias(places, 100, 0);
        int neutral = base + PlaceOpinionRouteRanker.routeBias(places, 200, 0);

        assertTrue(neutral > disliked);
    }

    @Test
    void mustHappen_opinionOffReturnsZeroBias() {
        OpinionFeatureGate.setTestOverride(false);
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        places.recordOutcome(new ChunkPos(0, 0).toLong(), -100f);

        assertEquals(0, PlaceOpinionRouteRanker.routeBias(places, 0, 0));
    }

    @Test
    void mustHappen_neutralPreferenceIsZeroBias() {
        OpinionFeatureGate.setTestOverride(true);
        assertEquals(0, PlaceOpinionRouteRanker.routeBias(new PlaceOpinionMemory(), 64, -32));
    }

    @Test
    void mustHappen_antiFixationDominatesPlaceBias() {
        int expeditionDestinationPenalty = 100;
        assertTrue(
                expeditionDestinationPenalty > PlaceOpinionRouteRanker.MAX_ROUTE_BIAS,
                "recent expedition destination memory must outweigh place tie-breaker");
    }

    @Test
    void mustNotHappen_extremeDislikeCannotVetoAllRoutes() {
        OpinionFeatureGate.setTestOverride(true);
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        places.recordOutcome(new ChunkPos(0, 0).toLong(), -100f);

        int worstBias = PlaceOpinionRouteRanker.routeBias(places, 0, 0);
        assertTrue(worstBias >= -PlaceOpinionRouteRanker.MAX_ROUTE_BIAS);
        assertTrue(worstBias > -50, "place bias must stay a soft preference, not a veto");
    }

    @Test
    void mustNotHappen_currentChunkDislikeDoesNotLowerExploreUtility() {
        AffectiveState mood = neutralMood();
        OpinionMemory opinions = new OpinionMemory();
        opinions.seedActivity(ActivityKind.OVERLAND_EXPLORATION, 20f, 5f, 0);

        ActivityUtilityBreakdown baseline = ActivityUtilityScorer.scoreExplore(
                mood, opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION));

        PlaceOpinionMemory places = new PlaceOpinionMemory();
        places.recordOutcome(new ChunkPos(0, 0).toLong(), -100f);
        assertEquals(-100f, places.preference(new ChunkPos(0, 0)));

        ActivityUtilityBreakdown afterDislikeRecorded = ActivityUtilityScorer.scoreExplore(
                mood, opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION));

        assertEquals(baseline.total(), afterDislikeRecorded.total(), 0.001f);
        assertEquals(baseline.preference(), afterDislikeRecorded.preference(), 0.001f);
    }

    private static AffectiveState neutralMood() {
        AffectiveState state = new AffectiveState();
        state.seedChannels(0f, 10f, 0f, 10f, 5f);
        return state;
    }
}
