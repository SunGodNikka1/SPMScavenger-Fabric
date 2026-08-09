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
            boolean combatTarget) {
        if (!OpinionFeatureGate.isEnabled()) {
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        if (context.isFrozen()) {
            return;
        }
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
                scoringInput));
    }

    public static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
