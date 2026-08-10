package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;

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
        boolean exploreAdoptionReady) {}
