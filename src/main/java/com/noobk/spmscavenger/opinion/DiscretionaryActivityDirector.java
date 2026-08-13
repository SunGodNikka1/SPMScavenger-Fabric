package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;

import java.util.UUID;

/**
 * GAO-4 — discretionary activity director entry point (PD-GAO-05 observer cadence).
 */
public final class DiscretionaryActivityDirector {

    private DiscretionaryActivityDirector() {}

    public static void tick(
            UUID mobId,
            long gameTime,
            ActivityObservationService.Observation observation,
            DiscretionaryAvailability availability,
            boolean combatTarget,
            ActivityAdmissions admissions,
            ActivityContinuations continuations) {
        if (!OpinionFeatureGate.isEnabled()) {
            MobExperienceContext existing = OpinionExperienceRegistry.find(mobId);
            if (existing == null) {
                return;
            }
            existing.discretionaryDirector().tick(new DirectorTickInput(
                    gameTime,
                    false,
                    existing.isFrozen(),
                    combatTarget,
                    observation,
                    new DiscretionaryScoringInput(
                            existing.affectiveState(),
                            existing.opinionMemory(),
                            availability,
                            false,
                            false),
                    admissions,
                    continuations));
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        boolean eligible = DiscretionaryEligibility.isDiscretionaryEligible(observation, combatTarget);
        DiscretionaryScoringInput scoringInput = new DiscretionaryScoringInput(
                context.affectiveState(),
                context.opinionMemory(),
                availability,
                eligible,
                true);
        context.discretionaryDirector().tick(new DirectorTickInput(
                gameTime,
                true,
                context.isFrozen(),
                combatTarget,
                observation,
                scoringInput,
                admissions,
                continuations));
    }

    public static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
