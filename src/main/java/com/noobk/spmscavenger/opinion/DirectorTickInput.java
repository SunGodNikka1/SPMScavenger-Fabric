package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;

import java.util.Optional;

/**
 * GAO-4 — one director observation tick input.
 */
public record DirectorTickInput(
        long gameTime,
        boolean opinionEnabled,
        boolean frozen,
        boolean combatTarget,
        ActivityObservationService.Observation observation,
        DiscretionaryScoringInput scoringInput,
        boolean exploreAdoptionReady,
        Optional<ExploreReadinessSnapshot> exploreReadiness) {

    public DirectorTickInput {
        exploreReadiness = exploreReadiness == null ? Optional.empty() : exploreReadiness;
    }
}
