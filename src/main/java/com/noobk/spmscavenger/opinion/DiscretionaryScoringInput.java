package com.noobk.spmscavenger.opinion;

import java.util.Objects;

/**
 * GAO-3 — inputs for discretionary utility scoring. Mood and opinions inform score only; they do
 * not grant scheduler authority.
 */
public record DiscretionaryScoringInput(
        AffectiveState affectiveState,
        OpinionMemory opinionMemory,
        DiscretionaryAvailability availability,
        boolean discretionaryEligible,
        boolean opinionEnabled) {

    public DiscretionaryScoringInput {
        Objects.requireNonNull(affectiveState, "affectiveState");
        Objects.requireNonNull(opinionMemory, "opinionMemory");
        Objects.requireNonNull(availability, "availability");
    }
}
