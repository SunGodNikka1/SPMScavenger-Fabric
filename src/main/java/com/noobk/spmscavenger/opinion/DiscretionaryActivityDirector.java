package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestCloseReason;

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
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        boolean opinionEnabled = OpinionFeatureGate.isEnabled();
        boolean eligible = opinionEnabled
                && DiscretionaryEligibility.isDiscretionaryEligible(observation, combatTarget);
        DiscretionaryScoringInput scoringInput = new DiscretionaryScoringInput(
                context.affectiveState(),
                context.opinionMemory(),
                availability,
                eligible,
                opinionEnabled);
        context.discretionaryDirector().tick(new DirectorTickInput(
                gameTime,
                opinionEnabled,
                context.isFrozen(),
                combatTarget,
                observation,
                scoringInput));
    }

    public static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
