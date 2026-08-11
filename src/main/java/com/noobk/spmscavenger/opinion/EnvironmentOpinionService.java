package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OutcomeClass;

/** GAO-9 — conservative attribution gate for semantic environment learning. */
public final class EnvironmentOpinionService {

    private EnvironmentOpinionService() {
    }

    public static void apply(
            EnvironmentOpinionMemory memory,
            EpisodeLearningEvidence evidence,
            PersonalityLearningResponse personality) {
        if (!OpinionFeatureGate.isEnabled() || !accepts(evidence)) {
            return;
        }
        EnvironmentProfile profile = evidence.environment().orElseThrow();
        float totalDelta = evidence.repetitionWeight()
                * personality.preferenceMultiplier()
                * OpinionMemory.TERMINAL_PREFERENCE_SCALE;
        memory.recordOutcome(profile, totalDelta);
    }

    static boolean accepts(EpisodeLearningEvidence evidence) {
        return evidence.activity().orElse(null) == ActivityKind.OVERLAND_EXPLORATION
                && evidence.terminalKind() == ExperienceKind.EXPEDITION_END
                && evidence.outcome() == OutcomeClass.VOLUNTARY_SUCCESS
                && evidence.cause() == ExperienceCause.EXPEDITION_COMPLETE
                && evidence.repetitionWeight() > 0f
                && evidence.environment().filter(profile -> !profile.isEmpty()).isPresent();
    }
}
