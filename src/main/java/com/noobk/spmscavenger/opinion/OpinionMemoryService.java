package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.EpisodeBoundaryPolicy;
import com.noobk.spmscavenger.experience.MobExperienceContext;

/**
 * GAO-2 — routes normalized learning evidence into per-mob {@link OpinionMemory}.
 */
public final class OpinionMemoryService {

    private OpinionMemoryService() {
    }

    public static void apply(MobExperienceContext context, EpisodeLearningEvidence evidence) {
        if (!OpinionFeatureGate.isEnabled() || context.isFrozen()) {
            return;
        }
        long duration = 0L;
        if (EpisodeBoundaryPolicy.closesEpisode(evidence.terminalKind(), evidence.cause())) {
            duration = context.episodeDuration(evidence.episodeId(), evidence.gameTime());
        }
        PersonalityLearningResponse response =
                context.personalityModel().learningResponse(evidence);
        context.opinionMemory().apply(evidence, duration, response);
        EnvironmentOpinionService.apply(context.environmentOpinionMemory(), evidence, response);
    }
}
