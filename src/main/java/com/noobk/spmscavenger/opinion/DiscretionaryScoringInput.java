package com.noobk.spmscavenger.opinion;

/**
 * GAO-3 — inputs for discretionary utility scoring. Mood and opinions inform score only; they do
 * not grant scheduler authority.
 *
 * <p>GAO-5B: place opinions influence expedition destination ranking ({@link PlaceOpinionRouteRanker}),
 * not EXPLORE activity utility at the mob's current position.
 */
public record DiscretionaryScoringInput(
        AffectiveState affectiveState,
        OpinionMemory opinionMemory,
        DiscretionaryAvailability availability,
        boolean discretionaryEligible,
        boolean opinionEnabled) {
}
