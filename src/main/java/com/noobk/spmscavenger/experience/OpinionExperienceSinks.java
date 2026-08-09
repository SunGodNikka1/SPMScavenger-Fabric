package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — ingress sinks for routed experience output. GAO-1 wires {@link AffectiveState} here.
 */
public interface OpinionExperienceSinks {

    void onAffectPulse(AffectPulse pulse);

    void onLearningEvidence(EpisodeLearningEvidence evidence);

    static OpinionExperienceSinks noOp() {
        return new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
            }
        };
    }
}
