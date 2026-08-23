package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2-E-R3 — the existing work route saying, in its own voice, <i>"I looked and there is nothing."</i>
 *
 * <h2>Why R2's seam accomplished nothing</h2>
 *
 * {@code reportRouteExhausted()} took no mob, no consumer, no tick and stored nothing — it returned
 * {@code INFEASIBLE} to whoever called it, and {@code status()} never consulted it. A gather goal
 * calling it would have watched the value die at the call site while the trade goal kept reading
 * {@code UNKNOWN}. It was a name, not a seam.
 *
 * <h2>Who may publish, and who may not</h2>
 *
 * <b>Only the existing-work owner</b>, and only after completing its own bounded search. That is the
 * whole point: the trade executor must never manufacture the evidence that authorizes trading, or it
 * has simply rebuilt gather inside itself and given itself permission.
 *
 * <p>Equally, <b>an interruption is not an exhaustion</b>. A gather goal preempted by combat, shelter
 * or a command has not finished looking, and publishing on {@code stop()} would convert every
 * interruption into "the route is dead" — the mob would trade its way around a fight.
 *
 * <h2>Positive evidence dominates</h2>
 *
 * Exhaustion is a memory of a past search; a live smelt plan or a fresh ore find is the present. So
 * {@link ExistingRouteFeasibility#status} checks feasibility <b>first</b> and consults this registry
 * only when it has nothing positive to report. "Failed once" never becomes temporary ownership, which
 * is what preserves V2-C's convergence.
 *
 * <h2>Gate RET-1</h2>
 *
 * Runtime-only, one entry per mob, keyed by stable mob UUID, hard-expiring. Released on demand
 * mismatch (the evidence is bound to a consumer and material), on permanent removal, and at server
 * stop. Not persisted: a remembered failure from a previous session would authorize trading for a
 * search nobody ran.
 */
public final class RouteExhaustionEvidence {

    /**
     * How long a completed-and-empty search stays meaningful.
     *
     * <p>Long enough to survive the walk to a village; short enough that the world has plausibly
     * changed by the time it lapses, so the mob re-checks rather than trusting an old failure.
     */
    public static final long EVIDENCE_LIFETIME_TICKS = 2_400L;

    /** Why the existing route reported itself finished. Kept for the readout, not for logic. */
    public enum Reason {
        /** A bounded search completed and found no actionable target. */
        SEARCH_COMPLETED_EMPTY,
        /** The route's own prerequisites cannot be met at all. */
        PREREQUISITE_UNAVAILABLE
    }

    private record Evidence(
            ResourceLocation consumerKey,
            ResourceLocation materialKey,
            Reason reason,
            long expiresAtTick) {
    }

    private static final Map<UUID, Evidence> EVIDENCE = new ConcurrentHashMap<>();

    private RouteExhaustionEvidence() {
    }

    /**
     * Publish a completed, empty bounded search.
     *
     * <p>Call only from the goal that owns the existing work route, and only when its search actually
     * ran to completion — never from {@code stop()} on an interruption.
     */
    public static void publish(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, Reason reason, long gameTime) {
        if (mobId == null || demand == null || reason == null) {
            return;
        }
        EVIDENCE.put(mobId, new Evidence(
                demand.consumerKey(), demand.materialKey(), reason,
                gameTime + EVIDENCE_LIFETIME_TICKS));
    }

    /**
     * Is there current evidence that <b>this</b> consumer's route for <b>this</b> material is
     * exhausted?
     *
     * <p>Identity must match on both. Evidence that the iron chain found nothing says nothing about
     * the torch chain, and letting it authorize a different consumer's trade would be the
     * stale-ownership defect wearing new clothes.
     */
    public static boolean exhaustedFor(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, long gameTime) {
        if (mobId == null || demand == null) {
            return false;
        }
        Evidence evidence = EVIDENCE.get(mobId);
        if (evidence == null) {
            return false;
        }
        if (gameTime >= evidence.expiresAtTick()) {
            EVIDENCE.remove(mobId, evidence);
            return false;
        }
        boolean matches = evidence.consumerKey().equals(demand.consumerKey())
                && evidence.materialKey().equals(demand.materialKey());
        if (!matches) {
            // Deleted, not merely ignored. Leaving it resident meant a torch-chain demand could
            // displace an iron demand and, if iron returned inside the lifetime, the OLD iron
            // search would authorize the NEW demand episode. New episode, new evidence.
            EVIDENCE.remove(mobId, evidence);
        }
        return matches;
    }

    /**
     * R5 — keep the evidence only while the consumer that produced it is still asking.
     *
     * <h2>The gap R4 left</h2>
     *
     * R4 deleted evidence when a <b>different</b> demand was queried, which covers a consumer being
     * displaced. It does not cover the ordinary success path, where the identity simply goes away and
     * comes back:
     *
     * <pre>
     * T1  gather searches, finds no iron, publishes iron exhaustion
     * T2  trade buys the iron
     * T3  the demand disappears        &lt;- nothing queries with a DIFFERENT demand here
     * T4  crafting consumes the iron
     * T5  the SAME consumer wants iron again, inside the 2400-tick lifetime
     * </pre>
     *
     * At T5 the old search would authorize a new episode. Nobody ever looked for the iron this
     * episode is about — <b>a demand that has been satisfied and re-raised is a new question</b>, and
     * evidence is only ever an answer to the question that was actually asked.
     *
     * <h2>Why this is not "clear on interruption"</h2>
     *
     * The signal is the <b>consumer's existence</b>, not the trade goal's activity. Combat preempting
     * the mob does not remove the demand, so this leaves legitimate search knowledge intact; only the
     * demand genuinely resolving or changing ends the episode.
     *
     * @param liveDemand the consumer's current demand, or {@code null} when it has none
     */
    public static void retainOnly(
            UUID mobId, WorkDemandPolicy.MaterialDemand liveDemand, long gameTime) {
        if (mobId == null) {
            return;
        }
        if (liveDemand == null) {
            EVIDENCE.remove(mobId);
            return;
        }
        // Delegated rather than duplicated: exhaustedFor already deletes on identity mismatch, and a
        // second copy of that rule is how the two drift apart.
        exhaustedFor(mobId, liveDemand, gameTime);
    }

    /** Drop the evidence — the route made progress, or the owner is gone. */
    public static void clear(UUID mobId) {
        if (mobId != null) {
            EVIDENCE.remove(mobId);
        }
    }

    public static void shutdownServerState() {
        EVIDENCE.clear();
    }

    public static int trackedCount() {
        return EVIDENCE.size();
    }

    /** Read-only debug-fixture preflight; never creates or consumes route authority. */
    public static boolean tracks(UUID mobId) {
        return mobId != null && EVIDENCE.containsKey(mobId);
    }
}
