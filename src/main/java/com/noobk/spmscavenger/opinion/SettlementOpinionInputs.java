package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;

import java.util.Objects;
import java.util.UUID;

/** Opinion-owned non-creating snapshot boundary for settlement destination ranking. */
public record SettlementOpinionInputs(
        DiscretionaryScoringInput scoring, SettlementOpinionContext context) {

    private static final SettlementOpinionInputs NEUTRAL = new SettlementOpinionInputs(
            new DiscretionaryScoringInput(
                    new AffectiveState(),
                    new OpinionMemory(),
                    DiscretionaryAvailability.none(),
                    false,
                    false),
            SettlementOpinionContext.neutral());

    public SettlementOpinionInputs {
        scoring = Objects.requireNonNull(scoring, "scoring");
        context = Objects.requireNonNull(context, "context");
    }

    public static SettlementOpinionInputs peek(UUID mobId) {
        MobExperienceContext existing = OpinionExperienceRegistry.find(mobId);
        if (existing == null) {
            return NEUTRAL;
        }
        return new SettlementOpinionInputs(
                new DiscretionaryScoringInput(
                        existing.affectiveState(),
                        existing.opinionMemory(),
                        DiscretionaryAvailability.none(),
                        false,
                        OpinionFeatureGate.isEnabled()),
                SettlementOpinionContext.from(existing.placeOpinionMemory()));
    }
}
