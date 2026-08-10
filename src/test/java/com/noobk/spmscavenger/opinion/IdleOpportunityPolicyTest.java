package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleOpportunityPolicyTest {

    @Test
    void highExplorePreferenceLowRepetitionExploreWins() {
        ScoringResult result = score(neutralMood(), opinions(55f, 5f, 11f, 4f));

        assertEquals(DiscretionaryActivity.EXPLORE, result.topActivity().orElseThrow());
        assertTrue(result.ranked().get(0).total() > result.ranked().get(1).total());
    }

    @Test
    void highExplorePreferenceExtremeRepetitionRestCanWin() {
        ScoringResult result = score(neutralMood(), opinions(55f, 48f, 11f, 4f));

        assertEquals(DiscretionaryActivity.REST, result.topActivity().orElseThrow());
        ActivityUtilityBreakdown explore = breakdown(result, DiscretionaryActivity.EXPLORE);
        assertTrue(explore.preference() > 0f, "preference stays positive");
        assertTrue(explore.repetition() < 0f, "repetition applies short-term pressure");
    }

    @Test
    void highStressRestGainsUtility() {
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 10f, 0f, 80f, 0f);
        ScoringResult result = score(mood, opinions(30f, 5f, 20f, 5f));

        ActivityUtilityBreakdown rest = breakdown(result, DiscretionaryActivity.REST);
        ActivityUtilityBreakdown explore = breakdown(result, DiscretionaryActivity.EXPLORE);
        assertTrue(rest.stressFit() > explore.stressFit());
        assertEquals(DiscretionaryActivity.REST, result.topActivity().orElseThrow());
    }

    @Test
    void highBoredomExploreGainsUtility() {
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 80f, 0f, 5f, 10f);
        ScoringResult result = score(mood, opinions(20f, 5f, 15f, 5f));

        ActivityUtilityBreakdown explore = breakdown(result, DiscretionaryActivity.EXPLORE);
        ActivityUtilityBreakdown rest = breakdown(result, DiscretionaryActivity.REST);
        assertTrue(explore.boredomFit() > rest.boredomFit());
        assertEquals(DiscretionaryActivity.EXPLORE, result.topActivity().orElseThrow());
    }

    @Test
    void mandatoryWorkSuppressesDiscretionaryScoring() {
        DiscretionaryScoringInput input = DiscretionaryScoringInput.withoutPlace(
                neutralMood(),
                opinions(55f, 5f, 11f, 4f),
                DiscretionaryAvailability.bothPresent(),
                false,
                true);

        assertTrue(IdleOpportunityPolicy.score(input).isEmpty());
    }

    @Test
    void missingExecutorExcludedBeforeScoring() {
        DiscretionaryScoringInput input = DiscretionaryScoringInput.withoutPlace(
                neutralMood(),
                opinions(55f, 5f, 11f, 4f),
                new DiscretionaryAvailability(true, false),
                true,
                true);

        ScoringResult result = IdleOpportunityPolicy.score(input).orElseThrow();
        assertEquals(1, result.ranked().size());
        assertEquals(DiscretionaryActivity.EXPLORE, result.topActivity().orElseThrow());
    }

    @Test
    void opinionDisabledProducesNoScoringEffect() {
        DiscretionaryScoringInput input = DiscretionaryScoringInput.withoutPlace(
                neutralMood(),
                opinions(55f, 5f, 11f, 4f),
                DiscretionaryAvailability.bothPresent(),
                true,
                false);

        assertTrue(IdleOpportunityPolicy.score(input).isEmpty());
    }

    @Test
    void identicalScoresProduceDeterministicTieBreak() {
        ActivityUtilityBreakdown explore = new ActivityUtilityBreakdown(
                DiscretionaryActivity.EXPLORE,
                10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -8f,
                42f);
        ActivityUtilityBreakdown rest = new ActivityUtilityBreakdown(
                DiscretionaryActivity.REST,
                8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f,
                42f);

        ScoringResult first = ScoringResult.of(List.of(explore, rest));
        ScoringResult second = ScoringResult.of(List.of(rest, explore));
        assertEquals(first.ranked(), second.ranked());
        assertEquals(DiscretionaryActivity.EXPLORE, first.topActivity().orElseThrow());
    }

    @Test
    void breakdownComponentsUseNormalizedInputsNotRawTicks() {
        OpinionMemory opinions = new OpinionMemory();
        opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION).seedForTest(0f, 0f, 0);
        opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION).setRecentDuration(12_000L);

        ActivityUtilityBreakdown explore = ActivityUtilityScorer.scoreExplore(neutralMood(),
                opinions.memoryOf(ActivityKind.OVERLAND_EXPLORATION));

        assertEquals(0f, explore.repetition(), 0.0001f,
                "raw recentDuration must not leak into repetition penalty");
    }

    private static ScoringResult score(AffectiveState mood, OpinionMemory opinions) {
        return IdleOpportunityPolicy.score(DiscretionaryScoringInput.withoutPlace(
                        mood,
                        opinions,
                        DiscretionaryAvailability.bothPresent(),
                        true,
                        true))
                .orElseThrow();
    }

    private static OpinionMemory opinions(
            float explorePreference,
            float exploreRepetition,
            float restPreference,
            float restRepetition) {
        OpinionMemory memory = new OpinionMemory();
        memory.seedActivity(ActivityKind.OVERLAND_EXPLORATION, explorePreference, exploreRepetition, 0);
        memory.seedActivity(ActivityKind.REST, restPreference, restRepetition, 0);
        return memory;
    }

    private static AffectiveState neutralMood() {
        AffectiveState state = new AffectiveState();
        state.seedChannels(0f, 10f, 0f, 10f, 5f);
        return state;
    }

    private static ActivityUtilityBreakdown breakdown(
            ScoringResult result, DiscretionaryActivity activity) {
        return result.ranked().stream()
                .filter(b -> b.activity() == activity)
                .findFirst()
                .orElseThrow();
    }
}
