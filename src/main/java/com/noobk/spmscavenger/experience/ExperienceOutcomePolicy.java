package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — learning eligibility and sign rules (D-GAO-022/023).
 */
public final class ExperienceOutcomePolicy {

    /** Repeated execution failures must accumulate before preference learning. */
    public static final int EXECUTION_FAILURE_LEARNING_THRESHOLD = 2;

    private ExperienceOutcomePolicy() {
    }

    public static boolean mayEmitAffect(OutcomeClass outcome) {
        return switch (outcome) {
            case VOLUNTARY_SUCCESS, VOLUNTARY_ABANDON, EXECUTION_FAILURE -> true;
            case ENVIRONMENT_UNAVAILABLE, SIMULATION_FRONTIER -> true;
            case PROTECTED_INTERRUPT, AUTHORITY_CANCEL -> false;
        };
    }

    public static boolean mayEmitPreferenceLearning(OutcomeClass outcome) {
        return outcome == OutcomeClass.VOLUNTARY_SUCCESS
                || outcome == OutcomeClass.VOLUNTARY_ABANDON;
    }

    public static boolean mayEmitFailureLearning(OutcomeClass outcome, int failureCount) {
        return outcome == OutcomeClass.EXECUTION_FAILURE
                && failureCount >= EXECUTION_FAILURE_LEARNING_THRESHOLD;
    }

    public static float preferenceSign(OutcomeClass outcome, ExperienceCause cause) {
        if (outcome == OutcomeClass.VOLUNTARY_SUCCESS) {
            return 1.0f;
        }
        if (outcome == OutcomeClass.VOLUNTARY_ABANDON) {
            return switch (cause) {
                case BOREDOM_THRESHOLD, MINING_NO_PROGRESS, MINING_BUDGET_EXHAUSTED -> -0.5f;
                case SOCIAL_FOLLOW, SOCIAL_COMPANION_INVITE -> 0.25f;
                default -> 0.0f;
            };
        }
        return 0.0f;
    }
}
