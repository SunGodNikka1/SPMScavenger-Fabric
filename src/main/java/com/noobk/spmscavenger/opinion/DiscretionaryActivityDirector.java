package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestCloseReason;
import net.minecraft.core.BlockPos;

import java.util.Optional;
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
            boolean exploreAdoptionReady,
            BlockPos placeAnchor) {
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
                    DiscretionaryScoringInput.withoutPlace(
                            existing.affectiveState(),
                            existing.opinionMemory(),
                            availability,
                            false,
                            false),
                    exploreAdoptionReady));
            return;
        }
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        boolean opinionEnabled = true;
        boolean eligible = DiscretionaryEligibility.isDiscretionaryEligible(observation, combatTarget);
        DiscretionaryScoringInput scoringInput = new DiscretionaryScoringInput(
                context.affectiveState(),
                context.opinionMemory(),
                context.placeOpinionMemory(),
                availability,
                eligible,
                opinionEnabled,
                Optional.ofNullable(placeAnchor));
        context.discretionaryDirector().tick(new DirectorTickInput(
                gameTime,
                opinionEnabled,
                context.isFrozen(),
                combatTarget,
                observation,
                scoringInput,
                exploreAdoptionReady));
    }

    public static DiscretionaryDirectorState stateFor(UUID mobId) {
        return OpinionExperienceRegistry.contextFor(mobId).discretionaryDirector();
    }
}
