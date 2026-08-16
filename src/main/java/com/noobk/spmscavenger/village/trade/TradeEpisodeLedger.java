package com.noobk.spmscavenger.village.trade;

/**
 * V2-G-R1 — at most one settlement relationship episode per {@link TradeChainPlan}.
 *
 * <h2>The gap the anchor alone left</h2>
 *
 * V2-G bounded credit with a single field cleared at teardown, which is correct <i>within</i> one
 * uninterrupted visit. But {@code TradeChainPlan} <b>deliberately survives {@code stop()}</b> — that
 * is the hard-lifetime invariant Option A was chosen to preserve — so teardown is not the end of the
 * chain:
 *
 * <pre>
 * SELL succeeds          -&gt; anchor captured
 * combat preempts        -&gt; stop() emits episode #1, clears the anchor
 * combat ends, resume    -&gt; SAME chain, still funding the SAME purchase
 * BUY succeeds           -&gt; anchor captured again
 * round ends             -&gt; emits episode #2
 * </pre>
 *
 * One bounded chain, two relationship episodes. {@code D-VR-063} says a visit teaches one village
 * relationship regardless of how many offer uses it contained, and an interruption in the middle does
 * not make it two visits.
 *
 * <h2>Why the credit still fires at the interruption</h2>
 *
 * The alternative — defer crediting until the chain completes — loses the episode entirely whenever
 * a chain is abandoned after a real trade, which is the common case in a world with hostile mobs. So
 * the decision stands: <b>credit immediately, then remember that this chain has spent its credit.</b>
 *
 * <h2>Lifetime</h2>
 *
 * Transient, one instance per goal, holding one reference to a record the goal already holds. Nothing
 * is persisted and no store is introduced, so Gate RET-1e has nothing to sweep — a chain that
 * disappears takes its ledger entry with it when {@link #onChainOpened()} runs for the next one.
 */
public final class TradeEpisodeLedger {

    private TradeChainPlan creditedChain;

    /**
     * May this visit emit a relationship episode?
     *
     * <p>Consuming: a {@code true} answer marks the chain spent, so the second call for the same
     * chain returns {@code false}.
     *
     * @param chain the chain the completed trade belongs to, or {@code null} when it has already
     *     terminated — a terminated chain cannot be resumed, so its credit is not at risk of reuse
     */
    public boolean consumeCreditFor(TradeChainPlan chain) {
        if (chain == null) {
            // The chain ended (target obtained, consumer gone, expired) before teardown. The trade
            // genuinely happened, and no resumption can double-credit it: the next chain is minted
            // by forDemand, which clears this ledger.
            return true;
        }
        if (creditedChain != null && creditedChain.sameChainAs(chain)) {
            return false;
        }
        creditedChain = chain;
        return true;
    }

    /**
     * A genuinely new chain was opened — the only event that restores credit.
     *
     * <p>Deliberately <b>not</b> called from {@code stop()}. Resetting on teardown is precisely the
     * defect this class exists to fix, and it would make the ledger a more elaborate spelling of the
     * field it replaced.
     */
    public void onChainOpened() {
        creditedChain = null;
    }

    /** Whether the current chain has already been credited. Diagnostics and tests only. */
    public boolean hasSpentCredit() {
        return creditedChain != null;
    }
}
