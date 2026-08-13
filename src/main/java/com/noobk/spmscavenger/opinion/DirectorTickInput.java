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

    /**
     * Test-only convenience. Production must pass real continuation snapshots: defaulting to
     * {@code none()} is exactly how the framework existed while the defect stayed live, since a
     * running incumbent reported NOT_RUNNING and was deleted from scoring anyway.
     */
    DirectorTickInput(
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
