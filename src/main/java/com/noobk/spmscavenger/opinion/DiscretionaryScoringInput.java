package com.noobk.spmscavenger.opinion;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

/**
 * GAO-3 — inputs for discretionary utility scoring. Mood and opinions inform score only; they do
 * not grant scheduler authority.
 */
public record DiscretionaryScoringInput(
        AffectiveState affectiveState,
        OpinionMemory opinionMemory,
        PlaceOpinionMemory placeOpinionMemory,
        DiscretionaryAvailability availability,
        boolean discretionaryEligible,
        boolean opinionEnabled,
        Optional<BlockPos> placeAnchor) {

    public DiscretionaryScoringInput {
        Objects.requireNonNull(affectiveState, "affectiveState");
        Objects.requireNonNull(opinionMemory, "opinionMemory");
        Objects.requireNonNull(placeOpinionMemory, "placeOpinionMemory");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(placeAnchor, "placeAnchor");
    }

    public static DiscretionaryScoringInput withoutPlace(
            AffectiveState affectiveState,
            OpinionMemory opinionMemory,
            DiscretionaryAvailability availability,
            boolean discretionaryEligible,
            boolean opinionEnabled) {
        return new DiscretionaryScoringInput(
                affectiveState,
                opinionMemory,
                new PlaceOpinionMemory(),
                availability,
                discretionaryEligible,
                opinionEnabled,
                Optional.empty());
    }
}
