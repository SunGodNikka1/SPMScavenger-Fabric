package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;

import java.util.Objects;

/** Pure GAO-8A mapping from observed affect/personality to bounded cosmetic attention. */
public final class PassiveExpressionPolicy {

    private static final int CALM_MIN_COOLDOWN = 50;
    private static final int CALM_MAX_COOLDOWN = 100;
    private static final int BORED_MIN_COOLDOWN = 20;
    private static final int BORED_MAX_COOLDOWN = 50;
    private static final int MIN_HOLD = 8;
    private static final int CALM_MAX_HOLD = 40;
    private static final int STRESSED_MAX_HOLD = 16;
    private static final float MIN_HORIZONTAL_RANGE = 45f;
    private static final float MAX_HORIZONTAL_RANGE = 120f;
    private static final float MIN_VERTICAL_RANGE = 15f;
    private static final float MAX_VERTICAL_RANGE = 40f;

    private PassiveExpressionPolicy() {
    }

    public static PassiveExpressionProfile evaluate(
            AffectiveState affect,
            PersonalityModel personality,
            ActivityObservationService.Observation observation,
            boolean opinionEnabled) {
        Objects.requireNonNull(affect, "affect");
        Objects.requireNonNull(personality, "personality");
        Objects.requireNonNull(observation, "observation");

        boolean safeSurface = observation.discretionaryIdleCandidate()
                || observation.resting()
                || observation.exploring();
        if (!opinionEnabled || !safeSurface || observation.unknownActive()) {
            return PassiveExpressionProfile.INACTIVE;
        }

        float boredom = positiveChannel(affect.boredom());
        float stress = positiveChannel(affect.stress());
        float novelty = positiveChannel(affect.novelty());
        float engagement = positiveChannel(affect.engagement());
        if (observation.exploring()
                && engagement > 0f
                && engagement >= Math.max(boredom, Math.max(stress, novelty))) {
            return PassiveExpressionProfile.INACTIVE;
        }
        float curiosity = Math.max(personality.curiosity(), novelty);
        float sociability = personality.sociability();

        int minCooldown = interpolate(CALM_MIN_COOLDOWN, BORED_MIN_COOLDOWN, boredom);
        int maxCooldown = interpolate(CALM_MAX_COOLDOWN, BORED_MAX_COOLDOWN, boredom);
        int maxHold = interpolate(CALM_MAX_HOLD, STRESSED_MAX_HOLD, stress);
        float horizontal = interpolate(MIN_HORIZONTAL_RANGE, MAX_HORIZONTAL_RANGE, curiosity);
        float vertical = interpolate(MIN_VERTICAL_RANGE, MAX_VERTICAL_RANGE, curiosity);

        PassiveExpressionTone tone = dominantTone(boredom, stress, curiosity, sociability);
        return new PassiveExpressionProfile(
                true,
                tone,
                minCooldown,
                maxCooldown,
                MIN_HOLD,
                Math.max(MIN_HOLD, maxHold),
                horizontal,
                vertical,
                sociability);
    }

    private static PassiveExpressionTone dominantTone(
            float boredom, float stress, float curiosity, float sociability) {
        float highest = stress;
        PassiveExpressionTone tone = stress > 0f
                ? PassiveExpressionTone.STRESSED
                : PassiveExpressionTone.NEUTRAL;
        if (boredom > highest) {
            highest = boredom;
            tone = PassiveExpressionTone.BORED;
        }
        if (curiosity > highest) {
            highest = curiosity;
            tone = PassiveExpressionTone.CURIOUS;
        }
        if (sociability > highest) {
            tone = PassiveExpressionTone.SOCIABLE;
        }
        return tone;
    }

    private static float positiveChannel(float value) {
        return Math.max(0f, Math.min(1f, value / AffectiveRates.CHANNEL_MAX));
    }

    private static int interpolate(int from, int to, float factor) {
        return Math.round(from + (to - from) * clamp01(factor));
    }

    private static float interpolate(float from, float to, float factor) {
        return from + (to - from) * clamp01(factor);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
