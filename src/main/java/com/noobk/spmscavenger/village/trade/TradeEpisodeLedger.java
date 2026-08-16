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
 * is persisted and no store is introduced, so Gate RET-1e has nothing to sweep: the single slot is
 * overwritten by the next chain that earns credit, never accumulated.
 *
 * <h2>R2 — no explicit reset, by design</h2>
 *
 * An earlier revision had the goal call {@code onChainOpened()} whenever {@code forDemand} minted a
 * chain. That made <b>planning mutate learning state</b>, and planning happens between a completed
 * transaction and its emission — so a pending episode could be credited after its own history had
 * been erased. {@link TradeChainPlan#sameChainAs} already restores eligibility naturally: a chain
 * with a different {@code createdAtTick} simply does not match the credited one. The reset was both
 * redundant and harmful, so there is no reset API at all.
 */
public final class TradeEpisodeLedger {

    private TradeChainPlan creditedChain;

    /**
     * May this visit emit a relationship episode?
     *
     * <p>Consuming: a {@code true} answer marks the chain spent, so the second call for the same
     * chain returns {@code false}.
     *
     * <h2>R3 — {@code null} fails closed</h2>
     *
     * Before R2 the caller passed the <b>live</b> chain, so {@code null} meant "the chain terminated
     * between the trade and teardown" — a legitimate episode, credited on the reasoning that the
     * next {@code forDemand} would clear the ledger anyway. R2 removed that reset and made the caller
     * pass the chain that <b>earned</b> the episode, captured at the transaction itself.
     *
     * <p>So {@code null} no longer means "terminated". It means <b>pending relationship evidence
     * arrived without its chain owner</b> — a state with no legitimate producer, since the anchor and
     * the chain are captured together in one guard. Crediting it would be crediting an episode
     * nothing can account for, so it is refused.
     *
     * @param chain the chain that earned this episode; {@code null} is a lost owner, never a
     *     terminated one
     */
    public boolean consumeCreditFor(TradeChainPlan chain) {
        if (chain == null) {
            return false;
        }
        if (creditedChain != null && creditedChain.sameChainAs(chain)) {
            return false;
        }
        creditedChain = chain;
        return true;
    }

    /** Whether the current chain has already been credited. Diagnostics and tests only. */
    public boolean hasSpentCredit() {
        return creditedChain != null;
    }
}
