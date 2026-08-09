package com.noobk.spmscavenger.opinion;

import java.util.Objects;

/**
 * GAO-3 — inspectable per-candidate utility decomposition for decision tracing.
 */
public record ActivityUtilityBreakdown(
        DiscretionaryActivity activity,
        float baseUsefulness,
        float preference,
        float boredomFit,
        float stressFit,
        float noveltyFit,
        float recentReward,
        float repetition,
        float failurePressure,
        float cost,
        float total) {

    public ActivityUtilityBreakdown {
        Objects.requireNonNull(activity, "activity");
    }

    public static ActivityUtilityBreakdown explore(
            float baseUsefulness,
            float preference,
            float boredomFit,
            float stressFit,
            float noveltyFit,
            float recentReward,
            float repetition,
            float failurePressure,
            float cost) {
        float total = clampTotal(
                baseUsefulness
                        + preference
                        + boredomFit
                        + stressFit
                        + noveltyFit
                        + recentReward
                        + repetition
                        + failurePressure
                        + cost);
        return new ActivityUtilityBreakdown(
                DiscretionaryActivity.EXPLORE,
                baseUsefulness,
                preference,
                boredomFit,
                stressFit,
                noveltyFit,
                recentReward,
                repetition,
                failurePressure,
                cost,
                total);
    }

    public static ActivityUtilityBreakdown rest(
            float baseUsefulness,
            float preference,
            float boredomFit,
            float stressFit,
            float recentReward,
            float repetition,
            float failurePressure,
            float cost) {
        float total = clampTotal(
                baseUsefulness
                        + preference
                        + boredomFit
                        + stressFit
                        + recentReward
                        + repetition
                        + failurePressure
                        + cost);
        return new ActivityUtilityBreakdown(
                DiscretionaryActivity.REST,
                baseUsefulness,
                preference,
                boredomFit,
                stressFit,
                0f,
                recentReward,
                repetition,
                failurePressure,
                cost,
                total);
    }

    private static float clampTotal(float total) {
        return Math.max(
                ActivityUtilityWeights.UTILITY_MIN,
                Math.min(ActivityUtilityWeights.UTILITY_MAX, total));
    }
}
