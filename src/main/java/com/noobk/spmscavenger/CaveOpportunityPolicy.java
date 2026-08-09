package com.noobk.spmscavenger;

/**
 * MI-6F — short-lived commitment to one cave opportunity.
 *
 * <p><b>The behaviour this exists to stop.</b> MI-6 ranks cave openings every time it replans. With
 * two comparable openings — left branch, right branch, or a branch versus the surface — tiny scoring
 * differences flip the winner between evaluations, and the mob visibly dithers at the junction
 * instead of committing to either. Ranking was correct on every individual evaluation; the sequence
 * was the defect.
 *
 * <p><b>The rule.</b> Once an opportunity is chosen it is <em>kept</em> until it expires, becomes
 * invalid, or a challenger beats it by more than {@link #SWITCH_MARGIN}. Equal-or-slightly-better
 * alternatives are refused deliberately: a mob that changes its mind for one point of score looks
 * broken even when the score is right.
 *
 * <p>Deliberately holds no world state and no position type — it is a pure arbitration rule so it
 * can be unit-tested without a level. The caller owns what the opportunity actually refers to.
 */
public final class CaveOpportunityPolicy {

    /**
     * A challenger must beat the held opportunity by more than this to take over. Sized above the
     * noise between comparable openings, below a genuine difference in kind such as
     * {@code CAVE_ORE_PRIORITY_BONUS}.
     */
    public static final int SWITCH_MARGIN = 8;

    /**
     * Commitment lifetime. Long enough to cross a junction and act, short enough that a stale
     * choice cannot outlive the situation that justified it.
     */
    public static final int COMMIT_TICKS = 200;

    private CaveOpportunityPolicy() {
    }

    /**
     * A commitment to one opportunity.
     *
     * @param id caller-owned identity — a packed block position, a branch index, whatever the
     *     caller ranks. Compared only for equality, never interpreted here.
     */
    public record CaveOpportunity(long id, int score, long committedTick) {

        public boolean expired(long now, int commitTicks) {
            return now - committedTick >= commitTicks;
        }

        public boolean isSame(long candidateId) {
            return id == candidateId;
        }
    }

    /**
     * Whether the held commitment survives this evaluation.
     *
     * @param held current commitment, or {@code null} when uncommitted
     * @param stillValid caller's check that the opportunity still exists and is reachable
     */
    public static boolean holds(CaveOpportunity held, boolean stillValid, long now, int commitTicks) {
        return held != null && stillValid && !held.expired(now, commitTicks);
    }

    /**
     * Whether a challenger should replace the held commitment.
     *
     * <p>Re-offering the <em>same</em> opportunity never counts as a switch, so a rising score on
     * what the mob already chose refreshes nothing and costs nothing.
     */
    public static boolean shouldSwitch(
            CaveOpportunity held, long candidateId, int candidateScore, int switchMargin) {
        if (held == null) {
            return true;
        }
        if (held.isSame(candidateId)) {
            return false;
        }
        return candidateScore > held.score() + switchMargin;
    }

    /**
     * The commitment to carry forward. Returns {@code held} unchanged when it stands, so a caller
     * can assign the result unconditionally.
     */
    public static CaveOpportunity arbitrate(
            CaveOpportunity held,
            boolean heldStillValid,
            long candidateId,
            int candidateScore,
            long now,
            int commitTicks,
            int switchMargin) {
        if (!holds(held, heldStillValid, now, commitTicks)) {
            return new CaveOpportunity(candidateId, candidateScore, now);
        }
        if (shouldSwitch(held, candidateId, candidateScore, switchMargin)) {
            return new CaveOpportunity(candidateId, candidateScore, now);
        }
        return held;
    }

    /** Convenience overload using the tuned defaults. */
    public static CaveOpportunity arbitrate(
            CaveOpportunity held, boolean heldStillValid, long candidateId, int candidateScore,
            long now) {
        return arbitrate(held, heldStillValid, candidateId, candidateScore, now,
                COMMIT_TICKS, SWITCH_MARGIN);
    }
}
