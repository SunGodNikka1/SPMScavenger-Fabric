package com.noobk.spmscavenger.opinion;

/**
 * Task 44B/44C — why a social target is, or is no longer, usable as an opportunity.
 *
 * <p>Every non-{@link #VALID} value is a distinct observable reason, because a decision that fails
 * silently is indistinguishable from one that was never attempted.
 *
 * <p>Note what is <b>absent</b>: there is no "relationship changed" cause. The observation carries
 * an identity SPM itself selected, so greet legality was already proven by the host at that moment,
 * and current legality is re-established at adoption by the live redirect — never by this addon
 * asking a question the host has already answered.
 */
public enum SocialTargetValidity {

    /** The remembered entity is present, alive, co-located and inside the host's own radius. */
    VALID,

    /** The mob has a combat target; SPM's own {@code canUse} returns false before it ever looks. */
    COMBAT_TARGET,

    /**
     * No fresh admission observation. The host has not recently reached its target-resolution call,
     * so it is on cooldown, already greeting, or being held by another authority.
     */
    NO_ADMISSION_EVIDENCE,

    /**
     * The host looked and named nobody. Overwhelmingly the common case — 98.4% of observed
     * admissions — and the reason an admission is not an opportunity.
     */
    NO_OBSERVED_TARGET,

    /** The named entity is gone: unloaded, despawned, or no longer resolvable by id. */
    TARGET_GONE,

    /** The named entity is resolvable but dead or removed. */
    TARGET_DEAD,

    /** The target is in another level — a UUID alone does not imply a shared dimension. */
    WRONG_LEVEL,

    /** The target drifted outside the host's own acquisition radius. */
    OUT_OF_RANGE;

    public boolean usable() {
        return this == VALID;
    }
}
