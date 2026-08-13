package com.noobk.spmscavenger.opinion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GAO-3 — scores available discretionary activities without selecting behavior.
 *
 * <p>Produces ranked utility only. {@code DiscretionaryActivityDirector} (GAO-4) consumes this
 * output later; this class issues no intents and mutates no Goals.
 */
public final class IdleOpportunityPolicy {

    private IdleOpportunityPolicy() {
    }

    /**
     * @return empty when opinion is disabled, discretionary context is blocked by mandatory work,
     *     or no executor-backed candidates exist
     */
    public static Optional<ScoringResult> score(DiscretionaryScoringInput input) {
        if (!input.opinionEnabled() || !input.discretionaryEligible()) {
            return Optional.empty();
        }

        List<ActivityUtilityBreakdown> candidates = new ArrayList<>(DiscretionaryActivity.values().length);
        for (DiscretionaryActivity activity : DiscretionaryActivity.values()) {
            if (!input.availability().hasExecutor(activity)) {
                continue;
            }
            // GAO-10: SOCIAL is about somebody. Scoring it with no subject would run the sociability
            // and entity-preference terms against nobody and produce a number that reads like a
            // considered judgement about a person who does not exist. Absence removes the candidate;
            // it does not make it merely unattractive. Today `hasExecutor(SOCIAL)` is false and hides
            // this, which is exactly why the gate belongs here rather than resting on that accident.
            if (activity == DiscretionaryActivity.SOCIAL && !input.socialCandidateAvailable()) {
                continue;
            }
            candidates.add(ActivityUtilityScorer.score(activity, input));
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ScoringResult.of(candidates));
    }
}
