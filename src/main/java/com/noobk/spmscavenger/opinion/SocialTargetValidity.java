package com.noobk.spmscavenger.opinion;

/**
 * Task 44B — why a social target is, or is no longer, usable.
 *
 * <p>Every non-{@link #VALID} value is a distinct observable reason, because "no social intent" with
 * no cause is the shape of finding that made this whole area expensive: a decision that fails
 * silently looks identical to one that was never attempted.
 */
public enum SocialTargetValidity {

    /** SPM would greet this exact entity right now. */
    VALID,

    /** SPM's relationship API is unreadable — fail closed, never assume friendship. */
    SPM_UNAVAILABLE,

    /** The mob has a combat target; SPM's own {@code canUse} returns false before it ever looks. */
    COMBAT_TARGET,

    /**
     * No fresh admission pulse. The host has not recently reached its target-resolution call, so it
     * is on cooldown, already greeting, or being held by another authority.
     */
    NO_ADMISSION_EVIDENCE,

    /** The remembered entity is gone: unloaded, despawned, or no longer resolvable by id. */
    TARGET_GONE,

    /** The remembered entity is resolvable but dead or removed. */
    TARGET_DEAD,

    /** The target is in another level — a UUID alone does not imply a shared dimension. */
    WRONG_LEVEL,

    /** The target drifted outside the host's own acquisition radius. */
    OUT_OF_RANGE,

    /** The relationship changed: SPM no longer reacts to this entity with GREET. */
    NOT_GREET_REACTION;

    public boolean usable() {
        return this == VALID;
    }
}
