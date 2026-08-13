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
        float subjectFit,
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
                0f,
                recentReward,
                repetition,
                failurePressure,
                cost,
                total);
    }

    /**
     * GAO-10 — SOCIAL's terms.
     *
     * <p>{@code subjectFit} exists as its own named field rather than reusing {@code noveltyFit},
     * because a slot whose name no longer describes its contents is how explanations start lying.
     * It carries the two subject-specific inputs — how sociable this mob is, and how it feels about
     * <em>this</em> entity — which is what makes SOCIAL the only activity that is about somebody.
     *
     * <p>Nothing here decides whether a greeting may happen. Opportunity is established before
     * scoring, and physical permission is established later still by the live host.
     */
    public static ActivityUtilityBreakdown social(
            float baseUsefulness,
            float preference,
            float boredomFit,
            float stressFit,
            float subjectFit,
            float recentReward,
            float repetition,
            float failurePressure,
            float cost) {
        float total = clampTotal(
                baseUsefulness
                        + preference
                        + boredomFit
                        + stressFit
                        + subjectFit
                        + recentReward
                        + repetition
                        + failurePressure
                        + cost);
        return new ActivityUtilityBreakdown(
                DiscretionaryActivity.SOCIAL,
                baseUsefulness,
                preference,
                boredomFit,
                stressFit,
                0f,
                subjectFit,
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
