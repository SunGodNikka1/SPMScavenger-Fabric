package com.noobk.spmscavenger.activity;

import com.noobk.spmscavenger.opinion.DiscretionaryEligibility;
import com.noobk.spmscavenger.opinion.InvalidationCause;

import java.util.Optional;

/**
 * D-VR-084 — the one shared discretionary-permission decision.
 *
 * <h2>Four states</h2>
 *
 * <pre>
 * mandatory executor RUNNING                    -&gt; block discretionary
 * route PENDING under a live published claim    -&gt; block discretionary
 * demand exists, no live claim (or it expired)  -&gt; DO NOT block
 * nothing mandatory                             -&gt; discretionary allowed
 * </pre>
 *
 * <h2>Demand does not create authority. A claim does.</h2>
 *
 * The running arm <b>delegates</b> to {@link DiscretionaryEligibility} — it never re-derives the
 * blocking set, because that re-derivation is exactly the duplicated-authority shape this decision
 * exists to remove. The pending arm consumes the registry's live claim; the third state needs no
 * viability judgement by anybody: an unservable demand either produces no claim or produces one
 * that expires, and discretionary work resumes structurally.
 *
 * <h2>Deliberate asymmetry</h2>
 *
 * The pending half fails <b>open</b> (an owner that forgets to publish does not block during its
 * pending window); the running half fails <b>closed</b> (an unclassified goal reads
 * {@code UNKNOWN_ACTIVE} and blocks). Do not "fix" the asymmetry without answering the second row
 * of the V2-DEF-002 repair gate.
 *
 * <p>Consumers: {@code DiscretionaryActivityDirector} and (V3-A, task-53)
 * {@code VillageWorkAdmission} — both consume the SAME permission.
 */
public final class MandatoryOwnership {

    private MandatoryOwnership() {
    }

    /** The decision: eligible plus the exact cause for inspector attribution. */
    public record Permission(boolean eligible, InvalidationCause cause) {
        public static Permission allowed() {
            return new Permission(true, InvalidationCause.NONE);
        }

        public static Permission denied(InvalidationCause cause) {
            return new Permission(false, cause);
        }
    }

    /**
     * Evaluate discretionary permission for one mob.
     *
     * @param observation the scheduler-wide observation (running truth)
     * @param combatTarget whether an attributable combat target is active
     * @param liveClaim the registry's live pending claim for this mob, if any (already
     *     expiry-deleted by the registry on read)
     * @param now current game tick
     */
    public static Permission evaluate(
            ActivityObservationService.Observation observation,
            boolean combatTarget,
            Optional<MandatoryOwnershipClaim> liveClaim,
            long now) {
        // Immediate self-defence first: combat is above every claim, and the running arm's own
        // eligibility would report COMBAT_TARGET too — this keeps the cause exact even when a
        // pending claim exists.
        if (combatTarget) {
            return Permission.denied(InvalidationCause.COMBAT_TARGET);
        }
        // Pending half: a live published claim means responsibility was accepted but the executor
        // has not started. It blocks with a distinct cause for the inspector.
        if (liveClaim != null && liveClaim.isPresent() && !liveClaim.get().expired(now)) {
            return Permission.denied(InvalidationCause.MANDATORY_PENDING_CLAIM);
        }
        // Running half: consume, never re-implement.
        if (!DiscretionaryEligibility.isDiscretionaryEligible(observation, combatTarget)) {
            return Permission.denied(
                    DiscretionaryEligibility.invalidationForObservation(observation, combatTarget));
        }
        return Permission.allowed();
    }
}
