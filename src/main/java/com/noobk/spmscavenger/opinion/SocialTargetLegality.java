package com.noobk.spmscavenger.opinion;

/**
 * Task 44B/44C — the single legality predicate for a social <em>opportunity</em>, over primitives.
 *
 * <h2>What this does and does not decide</h2>
 *
 * It answers "is the entity SPM recently named still physically usable?" — present, alive,
 * co-located, in range. It deliberately does <b>not</b> re-ask whether the host would greet them.
 * That question was already answered, by the host, when its own search returned this identity; and
 * asking it again would mean calling {@code reactionToward}, which transitively writes a per-tick
 * memo cache and could change the host's own later answer inside the same tick (D-GAO-057).
 *
 * <h2>Evidence levels</h2>
 *
 * <pre>
 * CHOOSE (44C)  recent host observation + these cheap live checks → SOCIAL may be scored
 * ADOPT  (44D)  the current redirect invocation + exact target-UUID equality → permission
 * RUN    (44D+) FriendlyGreet actually starts → causal ownership
 * </pre>
 *
 * A stale relationship can therefore make SOCIAL briefly <em>scoreable</em>, never <em>executable</em>:
 * adoption is refused unless the live host names the same entity again. And a refused adoption is
 * not a negative social outcome, so it cannot teach dislike.
 *
 * <h2>Why one shared function</h2>
 *
 * Discovery and re-validation ask the same question at two moments. Separate copies drift, and the
 * drift is invisible — a target legal enough to select but not to adopt yields an intent that can
 * never start. This project already paid for that once: two checks shared a <em>constant</em> but
 * not the <em>boundary</em>, one using {@code <} and the other {@code >}.
 */
public final class SocialTargetLegality {

    private SocialTargetLegality() {
    }

    /**
     * The host acquisition radius invariant, defined once.
     *
     * <p>{@code range > 0} alone is not enough: neither {@code NaN} nor {@code +Infinity} satisfies
     * {@code range <= 0}, so both slip through a positivity guard. {@code +Infinity} is the
     * dangerous one — it squares to {@code +Infinity}, every finite distance compares {@code <=}
     * against it, and the range boundary silently accepts the whole world. That is the exact inverse
     * of this project's fail-closed posture, and it would arrive as "the mob greeted someone
     * impossibly far away", not as an exception.
     *
     * <p>The radius is <b>external host evidence</b>, not a value this addon controls, so it is
     * validated wherever it enters the contract rather than trusted from its source.
     */
    public static boolean isUsableRadius(double range) {
        return Double.isFinite(range) && range > 0.0D;
    }

    /**
     * @param hasCombatTarget the host mob currently has a combat target
     * @param hasFreshObservation a non-stale admission observation exists for this mob
     * @param observationNamedTarget that observation carried an identity rather than "nobody"
     * @param observationNamesThisTarget that identity is the one under consideration. At discovery
     *     this holds by construction; at re-validation it is the load-bearing check, because the
     *     record is one-per-mob and overwritten, so the freshest answer may name someone else
     * @param targetResolved the named id resolved to a living entity in the world
     * @param targetAlive that entity is alive and not removed
     * @param sameLevel host and target share a level
     * @param distanceSqr squared distance between host and target
     * @param rangeSqr squared acquisition radius — the host's own, never one of ours
     */
    public static SocialTargetValidity check(
            boolean hasCombatTarget,
            boolean hasFreshObservation,
            boolean observationNamedTarget,
            boolean observationNamesThisTarget,
            boolean targetResolved,
            boolean targetAlive,
            boolean sameLevel,
            double distanceSqr,
            double rangeSqr) {

        // Ordered to mirror the host's own gate order, so a rejection here means what it would mean
        // inside SPM: combat first, then whether a usable target exists at all.
        if (hasCombatTarget) {
            return SocialTargetValidity.COMBAT_TARGET;
        }
        if (!hasFreshObservation) {
            return SocialTargetValidity.NO_ADMISSION_EVIDENCE;
        }
        if (!observationNamedTarget) {
            return SocialTargetValidity.NO_OBSERVED_TARGET;
        }
        if (!observationNamesThisTarget) {
            return SocialTargetValidity.TARGET_SUPERSEDED;
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
        if (!isUsableRadius(rangeSqr) || !(distanceSqr <= rangeSqr)) {
            // Two fail-closed guards in one branch:
            //  - an unusable bound (NaN, +Infinity, non-positive) can never be satisfied, so it
            //    rejects instead of admitting everything;
            //  - the comparison is a positive bound, so a NaN *distance* also rejects. Written as
            //    `distanceSqr > rangeSqr`, both would have passed.
            return SocialTargetValidity.OUT_OF_RANGE;
        }
        return SocialTargetValidity.VALID;
    }
}
