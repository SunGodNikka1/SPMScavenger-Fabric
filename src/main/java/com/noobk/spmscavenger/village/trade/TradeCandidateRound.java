package com.noobk.spmscavenger.village.trade;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * V2-E — one bounded attempt round over a candidate set.
 *
 * <h2>The failure this exists to prevent</h2>
 *
 * <pre>
 * choose A (best) -> path fails -> re-evaluate -> A still ranks best -> choose A -> path fails -> ...
 * </pre>
 *
 * Technically correct and visibly idiotic. <b>"Best-ranked offer is unreachable" is not "trade is
 * unreachable"</b> — a slightly worse but reachable villager B is right there.
 *
 * <h2>Why a round, and not a "decision cycle"</h2>
 *
 * "Demote for this decision cycle" is ambiguous: if the cycle ends when {@code canUse()} returns, the
 * demotion vanishes exactly when it is needed. A round spans attempts:
 *
 * <pre>
 * discover candidates -> rank A &gt; B &gt; C
 * attempt A -> path budget exhausted -> A attempted FOR THIS ROUND
 * attempt B -> fails                 -> B attempted
 * attempt C -> ...
 * all exhausted -> round ends -> failed-search cooldown -> fresh round (A eligible again)
 * </pre>
 *
 * No permanent blacklist and no {@code SavedData}. <b>V2-C's policy stays stateless; this executor
 * holds transient attempt state because physical movement unfolds over time.</b> That is the correct
 * boundary, not a compromise of it: a policy that answers "which route" needs no memory, while a
 * thing that walks does.
 *
 * <p>Per-mob and per-attempt; discarded whenever the goal stops.
 */
public final class TradeCandidateRound {

    /** Repath attempts for one candidate before it is demoted. */
    public static final int PATH_BUDGET_PER_CANDIDATE = 3;

    /**
     * Total approach ticks for one candidate before it is demoted, however navigation reports itself.
     *
     * <p>The path-failure budget only counts {@code moveTo} returning <b>false</b>. A path that is
     * <i>accepted</i> and then stalls against geometry consumes none of it, so without a wall-clock
     * bound an attempt can run forever while the claim quietly expires underneath it — and at that
     * moment the priority-1 greet can see the target again and preempt the very goal the interlock
     * was protecting. This bound is what makes "the attempt is bounded" true rather than intended.
     *
     * <p>Deliberately shorter than {@code TradeSessionClaimWindow.MAX_CLAIM_TICKS} so the attempt
     * always ends before its own backstop fires.
     */
    public static final int APPROACH_TICK_BUDGET_PER_CANDIDATE = 400;

    /** Ticks before a fresh round may open after every candidate failed. */
    public static final long EXHAUSTED_ROUND_COOLDOWN_TICKS = 200L;

    private final Set<UUID> attempted = new LinkedHashSet<>();
    private UUID current;
    private int pathFailures;
    private int approachTicks;
    private long cooldownUntilTick;

    /** Whether a candidate may be attempted in this round. */
    public boolean available(UUID villagerId) {
        return villagerId != null && !attempted.contains(villagerId);
    }

    /** Begin attempting a candidate. Idempotent for the candidate already in progress. */
    public void begin(UUID villagerId) {
        if (villagerId == null || villagerId.equals(current)) {
            return;
        }
        current = villagerId;
        pathFailures = 0;
        approachTicks = 0;
    }

    public UUID current() {
        return current;
    }

    /**
     * A repath failed.
     *
     * @return {@code true} when the budget is spent and the candidate has been demoted
     */
    public boolean recordPathFailure() {
        if (current == null) {
            return false;
        }
        if (++pathFailures >= PATH_BUDGET_PER_CANDIDATE) {
            demoteCurrent();
            return true;
        }
        return false;
    }

    /**
     * One tick spent approaching the current candidate.
     *
     * @return {@code true} when the approach budget is spent and the candidate has been demoted
     */
    public boolean recordApproachTick() {
        if (current == null) {
            return false;
        }
        if (++approachTicks >= APPROACH_TICK_BUDGET_PER_CANDIDATE) {
            demoteCurrent();
            return true;
        }
        return false;
    }

    public int approachTicks() {
        return approachTicks;
    }

    /**
     * Demote the current candidate — path budget spent, villager asleep, merchant occupied, offer
     * gone. All of those mean <i>this candidate is temporarily illegal</i>, which is a different
     * thing from <i>trading is impossible</i>, and the round is what keeps them different.
     */
    public void demoteCurrent() {
        if (current != null) {
            attempted.add(current);
            current = null;
            pathFailures = 0;
            approachTicks = 0;
        }
    }

    /** Every discovered candidate has been tried. */
    public boolean exhausted(int discoveredCandidateCount) {
        return attempted.size() >= discoveredCandidateCount;
    }

    /** Close the round and start the cooldown before a fresh one may open. */
    public void endRound(long gameTime) {
        attempted.clear();
        current = null;
        pathFailures = 0;
        approachTicks = 0;
        cooldownUntilTick = gameTime + EXHAUSTED_ROUND_COOLDOWN_TICKS;
    }

    public boolean coolingDown(long gameTime) {
        return gameTime < cooldownUntilTick;
    }

    /** Full reset, used when the goal stops for any reason. */
    public void clear() {
        attempted.clear();
        current = null;
        pathFailures = 0;
        approachTicks = 0;
    }

    public int attemptedCount() {
        return attempted.size();
    }
}
