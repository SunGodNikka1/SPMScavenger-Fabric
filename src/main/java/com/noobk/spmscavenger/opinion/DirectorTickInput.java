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
        ActivityAdmissions admissions,
        ActivityContinuations continuations) {

    public DirectorTickInput {
        admissions = admissions == null ? ActivityAdmissions.unavailable() : admissions;
        continuations = continuations == null ? ActivityContinuations.none() : continuations;
    }

    /** D-GAO-050 back-compat: a caller that supplies no continuation state has none running. */
    public DirectorTickInput(
            long gameTime,
            boolean opinionEnabled,
            boolean frozen,
            boolean combatTarget,
            ActivityObservationService.Observation observation,
            DiscretionaryScoringInput scoringInput,
            ActivityAdmissions admissions) {
        this(gameTime, opinionEnabled, frozen, combatTarget, observation, scoringInput, admissions,
                ActivityContinuations.none());
    }
}
