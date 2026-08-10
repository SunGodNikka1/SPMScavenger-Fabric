package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

/**
 * GAO-3 — per-candidate utility math. Mood influences score, not legality; preference and
 * repetition remain separate channels.
 */
public final class ActivityUtilityScorer {

    private ActivityUtilityScorer() {
    }

    public static ActivityUtilityBreakdown scoreExplore(
            AffectiveState affect, ActivityOpinionMemory memory) {
        return scoreExplore(affect, memory, new PlaceOpinionMemory(), Optional.empty());
    }

    public static ActivityUtilityBreakdown scoreExplore(
            AffectiveState affect,
            ActivityOpinionMemory memory,
            PlaceOpinionMemory places,
            Optional<BlockPos> anchor) {
        float placeAffinity = anchor
                .map(pos -> UtilityNormalizer.channel(places.preference(new ChunkPos(pos)))
                        * ActivityUtilityWeights.PLACE_PREFERENCE)
                .orElse(0f);
        float preference = UtilityNormalizer.channel(memory.preference()) * ActivityUtilityWeights.PREFERENCE
                + placeAffinity;
        float repetition =
                -UtilityNormalizer.repetitionPressure(memory.repetition()) * ActivityUtilityWeights.REPETITION;
        float recentReward =
                UtilityNormalizer.channel(memory.recentReward()) * ActivityUtilityWeights.RECENT_REWARD;
        float failurePressure =
                -UtilityNormalizer.failurePressure(memory.recentFailures()) * ActivityUtilityWeights.FAILURE;
        float boredomFit =
                UtilityNormalizer.channel(affect.boredom()) * ActivityUtilityWeights.EXPLORE_BOREDOM_FIT;
        float stressFit =
                -UtilityNormalizer.channel(affect.stress()) * ActivityUtilityWeights.EXPLORE_STRESS_FIT;
        float noveltyFit =
                UtilityNormalizer.channel(affect.novelty()) * ActivityUtilityWeights.EXPLORE_NOVELTY_FIT;
        float cost = -ActivityUtilityWeights.EXPLORE_COST;

        return ActivityUtilityBreakdown.explore(
                ActivityUtilityWeights.BASE_USEFULNESS_EXPLORE,
                preference,
                boredomFit,
                stressFit,
                noveltyFit,
                recentReward,
                repetition,
                failurePressure,
                cost);
    }

    public static ActivityUtilityBreakdown scoreRest(
            AffectiveState affect, ActivityOpinionMemory memory) {
        float preference = UtilityNormalizer.channel(memory.preference()) * ActivityUtilityWeights.PREFERENCE;
        float repetition =
                -UtilityNormalizer.repetitionPressure(memory.repetition()) * ActivityUtilityWeights.REPETITION;
        float recentReward =
                UtilityNormalizer.channel(memory.recentReward()) * ActivityUtilityWeights.RECENT_REWARD;
        float failurePressure =
                -UtilityNormalizer.failurePressure(memory.recentFailures()) * ActivityUtilityWeights.FAILURE;
        float stressFit =
                UtilityNormalizer.channel(affect.stress()) * ActivityUtilityWeights.REST_STRESS_FIT;
        float boredomFit =
                -UtilityNormalizer.channel(affect.boredom()) * ActivityUtilityWeights.REST_BOREDOM_FIT;
        float cost = -ActivityUtilityWeights.REST_COST;

        return ActivityUtilityBreakdown.rest(
                ActivityUtilityWeights.BASE_USEFULNESS_REST,
                preference,
                boredomFit,
                stressFit,
                recentReward,
                repetition,
                failurePressure,
                cost);
    }

    public static ActivityUtilityBreakdown score(
            DiscretionaryActivity activity, DiscretionaryScoringInput input) {
        ActivityKind kind = activity.opinionKind();
        ActivityOpinionMemory memory = input.opinionMemory().memoryOf(kind);
        return switch (activity) {
            case EXPLORE -> scoreExplore(
                    input.affectiveState(),
                    memory,
                    input.placeOpinionMemory(),
                    input.placeAnchor());
            case REST -> scoreRest(input.affectiveState(), memory);
        };
    }
}
