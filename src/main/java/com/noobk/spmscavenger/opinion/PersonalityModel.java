package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.OutcomeClass;

import java.util.Objects;

/**
 * GAO-7 — immutable, bounded interpretation model. It owns no scheduler or action authority.
 */
public record PersonalityModel(
        float curiosity,
        float sociability,
        float riskTolerance,
        float persistence,
        float materialism,
        float adventurousness) {

    public static final PersonalityModel NEUTRAL =
            new PersonalityModel(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);

    public PersonalityModel {
        curiosity = trait(curiosity);
        sociability = trait(sociability);
        riskTolerance = trait(riskTolerance);
        persistence = trait(persistence);
        materialism = trait(materialism);
        adventurousness = trait(adventurousness);
    }

    /**
     * Interpret evidence that has already passed causal/eligibility normalization.
     * Unsupported activity/cause pairs deliberately remain neutral.
     */
    public PersonalityLearningResponse learningResponse(EpisodeLearningEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.activity().isEmpty() || evidence.repetitionWeight() == 0.0f) {
            return PersonalityLearningResponse.NEUTRAL;
        }

        float weight = evidence.repetitionWeight();
        if (weight > 0.0f) {
            float sensitivity = positiveSensitivity(evidence.activity().orElseThrow());
            float multiplier = multiplierFor(sensitivity);
            return new PersonalityLearningResponse(multiplier, multiplier, 1.0f);
        }
        if (weight < 0.0f && evidence.outcome() == OutcomeClass.EXECUTION_FAILURE) {
            float resilience = evidence.cause() == ExperienceCause.MINING_HAZARD
                    ? riskTolerance
                    : persistence;
            // Greater resilience means a smaller negative preference response.
            return new PersonalityLearningResponse(1.0f, 1.0f, multiplierFor(1.0f - resilience));
        }
        return PersonalityLearningResponse.NEUTRAL;
    }

    private float positiveSensitivity(ActivityKind activity) {
        return switch (activity) {
            case OVERLAND_EXPLORATION -> average(curiosity, adventurousness);
            case CAVE_EXPLORATION -> average(curiosity, adventurousness, riskTolerance);
            case CONTROLLED_DESCENT, TUNNEL_SEARCH -> average(persistence, materialism);
            case RESOURCE_GATHERING -> materialism;
            case SOCIALIZING -> sociability;
            case REST, MIMICRY -> 0.5f;
        };
    }

    private static float multiplierFor(float sensitivity) {
        return PersonalityLearningResponse.MIN_MULTIPLIER
                + trait(sensitivity)
                * (PersonalityLearningResponse.MAX_MULTIPLIER
                - PersonalityLearningResponse.MIN_MULTIPLIER);
    }

    private static float average(float... values) {
        float total = 0.0f;
        for (float value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static float trait(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("personality trait must be finite");
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
