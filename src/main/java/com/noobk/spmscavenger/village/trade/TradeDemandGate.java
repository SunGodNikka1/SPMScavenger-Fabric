package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.WorkDemandPolicy;

import java.util.Optional;

/**
 * V2-E — a thin executor-admission seam. <b>It answers one question and owns nothing.</b>
 *
 * <blockquote>Is TRADE the currently authorized acquisition route for the current live demand?</blockquote>
 *
 * <h2>What it must never become</h2>
 *
 * The authorities already exist and this class defers to both, every call:
 *
 * <ul>
 *   <li>{@code WorkDemandPolicy} owns <b>whether a demand exists</b>;</li>
 *   <li>{@link TradeDemandRegistrar} owns <b>which route serves it</b>, statelessly.</li>
 * </ul>
 *
 * So this gate does not invent demand, does not cache route ownership, does not rank, and does not
 * arbitrate. It exists because a {@code Goal} needs one boolean at {@code canUse()} time, not because
 * the decision needed a new home. A second director hidden behind an innocuous name is exactly the
 * creep V2-C's structural tests were written to prevent, and this class is the most likely place for
 * it to reappear.
 *
 * <p><b>Nothing is remembered between calls.</b> Each call re-reads the live demand and re-runs the
 * route decision, so a demand that disappears mid-walk stops authorizing the trade on the very next
 * tick — which is what makes "recompute at the attempt boundary" honest rather than decorative.
 */
public final class TradeDemandGate {

    /** The live demand and the route chosen for it, or empty when TRADE is not authorized now. */
    public record Authorization(
            WorkDemandPolicy.MaterialDemand demand,
            TradeDemandRegistrar.AcquisitionDecision decision) {

        public java.util.List<TradeEvaluation> rankedOffers() {
            return decision.rankedTradeOffers();
        }
    }

    private TradeDemandGate() {
    }

    /**
     * @param liveDemand the demand as selected <b>right now</b> by {@code WorkDemandPolicy}, never a
     *     cached copy — a stale demand here would authorize a trade nobody wants
     * @param evidence current bounded route evidence
     */
    public static Optional<Authorization> authorize(
            WorkDemandPolicy.MaterialDemand liveDemand, RouteEvidence evidence) {
        if (liveDemand == null || evidence == null) {
            return Optional.empty();
        }
        TradeDemandRegistrar.AcquisitionDecision decision =
                TradeDemandRegistrar.decide(liveDemand, evidence);
        if (!decision.tradeChosen()) {
            return Optional.empty();
        }
        return Optional.of(new Authorization(liveDemand, decision));
    }
}
