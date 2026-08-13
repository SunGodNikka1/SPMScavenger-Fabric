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

    /**
     * D-GAO-024 — the foundational learning-eligibility rule: outcome <b>and</b> cause.
     *
     * <p>Coarse outcome alone is not enough. {@code OpinionLearningPolicy} already suppressed a set
     * of causes that must never teach preference regardless of their outcome class, but it was the
     * <em>only</em> place causes mattered — so activity learning honoured them and place learning,
     * which does not route through that policy, did not.
     *
     * <p>Currently harmless only by coincidence: the dangerous mining terminals fail the coarse
     * check or carry zero magnitude anyway. The trap is a future terminal that is
     * {@code EXECUTION_FAILURE} with a protected cause and enough repetitions — eligible here,
     * rejected there, and D-GAO-024's divergence is back.
     *
     * @param failureCount repetitions, for the {@link #EXECUTION_FAILURE_LEARNING_THRESHOLD} channel
     */
    public static boolean mayEmitLearning(
            OutcomeClass outcome, ExperienceCause cause, int failureCount) {
        if (isSuppressedCause(cause)) {
            return false;
        }
        return mayEmitPreferenceLearning(outcome)
                || mayEmitFailureLearning(outcome, failureCount);
    }

    /**
     * Causes that never teach preference, whatever their outcome class.
     *
     * <p>Authority, protection, simulation limits and environmental blocking describe why the
     * <em>mod</em> stopped, not what the <em>world</em> is like.
     */
    public static boolean isSuppressedCause(ExperienceCause cause) {
        return cause == ExperienceCause.AUTHORITY_CANCEL
                || cause == ExperienceCause.PROTECTED_INTERRUPT
                || cause == ExperienceCause.SIMULATION_FRONTIER
                || cause == ExperienceCause.ENVIRONMENT_BLOCKED
                || cause == ExperienceCause.MINING_PLAYER_ORDER
                || cause == ExperienceCause.REST_COMBAT
                || cause == ExperienceCause.REST_MANDATORY_WORK;
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
