package com.noobk.spmscavenger.opinion;

/**
 * Task 44B — the single legality predicate for a social target, over primitives only.
 *
 * <h2>Why one shared function</h2>
 *
 * Resolution and re-validation ask the same question at two different moments. If each owned its own
 * copy of the rule they would drift, and the drift would be invisible: a target legal enough to
 * select but not to adopt produces an intent that can never start, forever. This project has already
 * paid for that lesson once — sharing a <em>constant</em> between two checks did not share the
 * <em>boundary</em>, because one used {@code <} and the other {@code >}. So the predicate itself is
 * the shared thing, not its inputs.
 *
 * <h2>The rule this encodes</h2>
 *
 * <pre>
 * admission pulse exists  ≠  social target available
 * </pre>
 *
 * 98.4% of observed pulses carried {@code targetFound=false}, so pulse presence is nearly always
 * true and means only "the host recently got as far as looking". Even a pulse that <em>did</em> find
 * somebody is evidence about a moment up to {@code PULSE_LIFETIME_TICKS} ago; in that window the
 * target can move, die, turn hostile, unload, or change dimension.
 *
 * <p><b>Evidence that an action was recently possible is not authority to perform it now.</b> That
 * is why every term here is re-evaluated against live state at adoption, and why the entity is
 * carried as an id rather than a reference.
 */
public final class SocialTargetLegality {

    private SocialTargetLegality() {
    }

    /**
     * @param spmAvailable SPM's relationship API is readable
     * @param hasCombatTarget the host mob currently has a combat target
     * @param hasFreshAdmission a non-stale admission pulse exists for this mob
     * @param targetResolved the remembered id resolved to an entity in the world
     * @param targetAlive that entity is alive and not removed
     * @param sameLevel host and target share a level
     * @param distanceSqr squared distance between host and target
     * @param rangeSqr squared acquisition radius — the host's own, never one of ours
     * @param greetReaction SPM answered GREET for this exact entity, now
     */
    public static SocialTargetValidity check(
            boolean spmAvailable,
            boolean hasCombatTarget,
            boolean hasFreshAdmission,
            boolean targetResolved,
            boolean targetAlive,
            boolean sameLevel,
            double distanceSqr,
            double rangeSqr,
            boolean greetReaction) {

        // Ordered to mirror the host's own gate order, so a rejection here means the same thing it
        // would mean inside SPM: cooldown/combat first, then whether a legal target exists.
        if (!spmAvailable) {
            return SocialTargetValidity.SPM_UNAVAILABLE;
        }
        if (hasCombatTarget) {
            return SocialTargetValidity.COMBAT_TARGET;
        }
        if (!hasFreshAdmission) {
            return SocialTargetValidity.NO_ADMISSION_EVIDENCE;
        }
        if (!targetResolved) {
            return SocialTargetValidity.TARGET_GONE;
        }
        if (!targetAlive) {
            return SocialTargetValidity.TARGET_DEAD;
        }
        if (!sameLevel) {
            return SocialTargetValidity.WRONG_LEVEL;
        }
        if (!(distanceSqr <= rangeSqr)) {
            // NaN-safe by construction: written as a positive bound so an unmeasurable distance
            // falls through to rejection rather than passing.
            return SocialTargetValidity.OUT_OF_RANGE;
        }
        if (!greetReaction) {
            return SocialTargetValidity.NOT_GREET_REACTION;
        }
        return SocialTargetValidity.VALID;
    }
}
