package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceOpinionScoringTest {

    @Test
    void positivePlacePreferenceBoostsExploreUtility() {
        AffectiveState mood = neutralMood();
        OpinionMemory opinions = neutralOpinions();
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        BlockPos anchor = new BlockPos(16, 64, 16);
        places.recordOutcome(new ChunkPos(anchor).toLong(), 80f);

        ActivityUtilityBreakdown without = ActivityUtilityScorer.scoreExplore(
                mood, opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION));
        ActivityUtilityBreakdown with = ActivityUtilityScorer.scoreExplore(
                mood,
                opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION),
                places,
                Optional.of(anchor));

        assertTrue(with.total() > without.total());
        assertTrue(with.preference() > without.preference());
    }

    @Test
    void negativePlacePreferenceReducesExploreUtility() {
        AffectiveState mood = neutralMood();
        OpinionMemory opinions = neutralOpinions();
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        BlockPos anchor = new BlockPos(0, 64, 0);
        places.recordOutcome(new ChunkPos(anchor).toLong(), -80f);

        ActivityUtilityBreakdown without = ActivityUtilityScorer.scoreExplore(
                mood, opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION));
        ActivityUtilityBreakdown with = ActivityUtilityScorer.scoreExplore(
                mood,
                opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION),
                places,
                Optional.of(anchor));

        assertTrue(with.total() < without.total());
    }

    @Test
    void placeAffinityCanTipExploreOverRest() {
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 45f, 0f, 10f, 5f);
        OpinionMemory opinions = new OpinionMemory();
        opinions.seedActivity(ActivityKind.OVERLAND_EXPLORATION, 20f, 5f, 0);
        opinions.seedActivity(ActivityKind.REST, 22f, 5f, 0);
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        BlockPos anchor = new BlockPos(8, 64, 8);
        places.recordOutcome(new ChunkPos(anchor).toLong(), 90f);

        ScoringResult result = IdleOpportunityPolicy.score(new DiscretionaryScoringInput(
                        mood,
                        opinions,
                        places,
                        DiscretionaryAvailability.bothPresent(),
                        true,
                        true,
                        Optional.of(anchor)))
                .orElseThrow();

        assertTrue(result.topActivity().orElseThrow() == DiscretionaryActivity.EXPLORE);
    }

    private static AffectiveState neutralMood() {
        AffectiveState state = new AffectiveState();
        state.seedChannels(0f, 10f, 0f, 10f, 5f);
        return state;
    }

    private static OpinionMemory neutralOpinions() {
        OpinionMemory memory = new OpinionMemory();
        memory.seedActivity(ActivityKind.OVERLAND_EXPLORATION, 20f, 5f, 0);
        memory.seedActivity(ActivityKind.REST, 20f, 5f, 0);
        return memory;
    }
}
