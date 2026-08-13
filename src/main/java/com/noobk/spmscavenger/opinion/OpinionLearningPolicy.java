package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.EpisodeBoundaryPolicy;
import com.noobk.spmscavenger.experience.EpisodeNormalizationPolicy;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceOutcomePolicy;
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

    /**
     * D-GAO-024 — delegates to the foundational rule rather than owning the list.
     *
     * <p>Retained as defence in depth: this policy still refuses evidence with no activity and
     * still rejects the protected outcome classes outright. What it no longer does is be the only
     * place in the codebase where causes are consulted.
     */
    private static boolean blockedCause(ExperienceCause cause) {
        return ExperienceOutcomePolicy.isSuppressedCause(cause);
    }

    public static void apply(
            ActivityOpinionMemory memory,
            EpisodeLearningEvidence evidence,
            long episodeDurationTicks) {
        apply(memory, evidence, episodeDurationTicks, PersonalityLearningResponse.NEUTRAL);
    }

    public static void apply(
            ActivityOpinionMemory memory,
            EpisodeLearningEvidence evidence,
            long episodeDurationTicks,
            PersonalityLearningResponse personality) {
        if (!accepts(evidence)) {
            return;
        }
        float weight = evidence.repetitionWeight();
        if (weight == 0f && !EpisodeBoundaryPolicy.closesEpisode(evidence.terminalKind(), evidence.cause())) {
            return;
        }

        memory.setLastPerformed(evidence.gameTime());

        if (EpisodeNormalizationPolicy.isMilestone(evidence.terminalKind())) {
            memory.addRepetition(Math.abs(weight) * OpinionMemory.MILESTONE_REPETITION_SCALE);
            if (weight > 0f) {
                memory.addRecentReward(weight * personality.rewardMultiplier());
                memory.addPreference(weight
                        * personality.preferenceMultiplier()
                        * OpinionMemory.MILESTONE_PREFERENCE_SCALE);
            }
            return;
        }

        if (EpisodeBoundaryPolicy.closesEpisode(evidence.terminalKind(), evidence.cause())) {
            memory.setRecentDuration(episodeDurationTicks);
            memory.addRepetition(sessionRepetition(episodeDurationTicks));
            if (weight > 0f) {
                memory.addPreference(weight
                        * personality.preferenceMultiplier()
                        * OpinionMemory.TERMINAL_PREFERENCE_SCALE);
                memory.addRecentReward(weight * personality.rewardMultiplier());
            } else if (weight < 0f) {
                memory.addPreference(weight
                        * personality.failurePreferenceMultiplier()
                        * OpinionMemory.TERMINAL_PREFERENCE_SCALE);
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
