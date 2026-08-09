package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.EpisodeNormalizationPolicy;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.OutcomeClass;

/**
 * GAO-2 — defense-in-depth gates before applying normalized episode evidence.
 */
public final class OpinionLearningPolicy {

    private OpinionLearningPolicy() {
    }

    public static boolean accepts(EpisodeLearningEvidence evidence) {
        if (evidence.activity().isEmpty()) {
            return false;
        }
        return switch (evidence.outcome()) {
            case VOLUNTARY_SUCCESS, VOLUNTARY_ABANDON -> !blockedCause(evidence.cause());
            case EXECUTION_FAILURE -> !blockedCause(evidence.cause());
            case ENVIRONMENT_UNAVAILABLE, SIMULATION_FRONTIER, PROTECTED_INTERRUPT, AUTHORITY_CANCEL ->
                    false;
        };
    }

    private static boolean blockedCause(ExperienceCause cause) {
        return cause == ExperienceCause.AUTHORITY_CANCEL
                || cause == ExperienceCause.PROTECTED_INTERRUPT
                || cause == ExperienceCause.SIMULATION_FRONTIER
                || cause == ExperienceCause.ENVIRONMENT_BLOCKED
                || cause == ExperienceCause.MINING_PLAYER_ORDER
                || cause == ExperienceCause.REST_COMBAT
                || cause == ExperienceCause.REST_MANDATORY_WORK;
    }

    public static void apply(
            ActivityOpinionMemory memory,
            EpisodeLearningEvidence evidence,
            long episodeDurationTicks) {
        if (!accepts(evidence)) {
            return;
        }
        float weight = evidence.repetitionWeight();
        if (weight == 0f && !EpisodeNormalizationPolicy.isTerminal(evidence.terminalKind())) {
            return;
        }

        memory.setLastPerformed(evidence.gameTime());

        if (EpisodeNormalizationPolicy.isMilestone(evidence.terminalKind())) {
            memory.addRepetition(Math.abs(weight) * OpinionMemory.MILESTONE_REPETITION_SCALE);
            if (weight > 0f) {
                memory.addRecentReward(weight);
                memory.addPreference(weight * OpinionMemory.MILESTONE_PREFERENCE_SCALE);
            }
            return;
        }

        if (EpisodeNormalizationPolicy.isTerminal(evidence.terminalKind())) {
            memory.setRecentDuration(episodeDurationTicks);
            memory.addRepetition(sessionRepetition(episodeDurationTicks));
            if (weight > 0f) {
                memory.addPreference(weight * OpinionMemory.TERMINAL_PREFERENCE_SCALE);
                memory.addRecentReward(weight);
            } else if (weight < 0f) {
                memory.addPreference(weight * OpinionMemory.TERMINAL_PREFERENCE_SCALE);
                memory.recordFailure();
            }
        }
    }

    private static float sessionRepetition(long durationTicks) {
        if (durationTicks <= 0L) {
            return 0f;
        }
        // Long sessions raise repetition without automatically collapsing preference.
        return Math.min(40f, durationTicks / (float) OpinionMemory.LONG_SESSION_TICKS);
    }
}
