package com.noobk.spmscavenger.opinion;

/**
 * GAO-7 — bounded subjective response applied to an already-eligible learning delta.
 *
 * <p>Every multiplier is strictly positive. This type may scale a delta, but cannot create one,
 * invert its sign, or make rejected evidence eligible.
 */
public record PersonalityLearningResponse(
        float preferenceMultiplier,
        float rewardMultiplier,
        float failurePreferenceMultiplier) {

    public static final float MIN_MULTIPLIER = 0.75f;
    public static final float MAX_MULTIPLIER = 1.25f;
    public static final PersonalityLearningResponse NEUTRAL =
            new PersonalityLearningResponse(1.0f, 1.0f, 1.0f);

    public PersonalityLearningResponse {
        preferenceMultiplier = bounded(preferenceMultiplier);
        rewardMultiplier = bounded(rewardMultiplier);
        failurePreferenceMultiplier = bounded(failurePreferenceMultiplier);
    }

    private static float bounded(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("personality learning multiplier must be finite");
        }
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }
}
