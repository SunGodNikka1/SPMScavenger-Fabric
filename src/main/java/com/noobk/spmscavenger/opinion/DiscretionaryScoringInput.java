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
        boolean opinionEnabled,
        java.util.Optional<SocialIntent> socialOpportunity,
        float sociability,
        float subjectPreference,
        float settlementSocialBias) {

    public DiscretionaryScoringInput {
        java.util.Objects.requireNonNull(socialOpportunity, "socialOpportunity");
    }

    /**
     * Backwards-compatible form for callers with no social opportunity — the disabled path and
     * every existing test. Absent opportunity means SOCIAL is not a candidate at all, rather than a
     * candidate scoring badly.
     */
    public DiscretionaryScoringInput(
            AffectiveState affectiveState,
            OpinionMemory opinionMemory,
            DiscretionaryAvailability availability,
            boolean discretionaryEligible,
            boolean opinionEnabled) {
        this(affectiveState, opinionMemory, availability, discretionaryEligible, opinionEnabled,
                java.util.Optional.empty(), 0f, 0f, 0f);
    }

    public DiscretionaryScoringInput(
            AffectiveState affectiveState,
            OpinionMemory opinionMemory,
            DiscretionaryAvailability availability,
            boolean discretionaryEligible,
            boolean opinionEnabled,
            java.util.Optional<SocialIntent> socialOpportunity,
            float sociability,
            float subjectPreference) {
        this(affectiveState, opinionMemory, availability, discretionaryEligible, opinionEnabled,
                socialOpportunity, sociability, subjectPreference, 0f);
    }

    /**
     * GAO-10 — SOCIAL competes only while a validated subject exists.
     *
     * <p>This is candidacy, not permission. The director's own admission and executor start gates
     * still apply, and physical start additionally requires the live host to name this same entity
     * (44D). A fresh target-bearing observation buys a seat at the table, nothing more.
     */
    public boolean socialCandidateAvailable() {
        return socialOpportunity.isPresent();
    }
}
