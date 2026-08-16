package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.WorkDemandPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * V2-C — is trading a legitimate way to satisfy a demand that already exists?
 *
 * <h2>What the common acquisition model compares</h2>
 *
 * <b>Structural facts, never blended numbers.</b> V2-B's utility and {@code WorkDemandPolicy}'s
 * {@code derivedUtility} do not share units: {@code 73} trade utility against {@code 100} smelt
 * utility is not a comparison, and converting emerald price into mining effort would be a magic
 * constant wearing the costume of one. So the route choice reads only:
 *
 * <pre>
 * is the existing route feasible?
 * is there current bounded offer evidence?
 * can the mob currently pay?
 * </pre>
 *
 * {@link TradeEvaluation#utility()} is consulted <b>only</b> to order offers once TRADE has already
 * been chosen (gate 6). If a future slice needs a genuine cross-strategy comparison it must build a
 * model whose units are defined, not reuse these.
 *
 * <h2>The asymmetry is deliberate, and it is what makes this converge</h2>
 *
 * TRADE is preferred only when the existing route is <b>infeasible</b>. It never wins on being more
 * attractive (gate 3), and refusing it never blocks the existing route (gate 7) — a policy that could
 * turn a satisfiable demand into {@code BLOCKED} would suppress progression while looking like a
 * preference.
 *
 * <p>That asymmetry also removes oscillation: with no tie to break on a score, two nearly-equal
 * options cannot flip ownership every tick. Combined with statelessness (gate 9), the decision is a
 * pure function of present evidence — so a disappeared candidate needs no invalidation, and returning
 * to earlier evidence returns the earlier decision.
 *
 * <h2>What it refuses to do</h2>
 *
 * Creates no demand (gate 1). Synthesises no emerald deficit (gate 2). Performs no transaction
 * (gate 8) and no movement (gate 10). Holds no state and reads no clock.
 */
public final class TradeDemandRegistrar {

    /** Which acquisition strategy owns this demand right now. */
    public enum AcquisitionRoute {
        /** Gather / smelt / craft, as `WorkDemandPolicy` already provides. */
        EXISTING_WORK,
        /** Buy or sell through a villager. */
        TRADE
    }

    /** Why TRADE was not chosen. Never a reason the demand itself becomes unsatisfiable. */
    public enum TradeBlockedReason {
        /** The existing route can do it; trade is not needed (gate 7). */
        EXISTING_ROUTE_AVAILABLE,
        /** No bounded offer evidence right now (gate 4). */
        NO_OFFER_EVIDENCE,
        /** Offers exist, none contributes to this demand. */
        NO_VIABLE_OFFER,
        /** A viable offer exists but the mob cannot currently pay for it. */
        PAYMENT_UNAVAILABLE
    }

    /**
     * @param rankedTradeOffers ordered best-first by V2-B utility; empty unless {@code route} is
     *     {@code TRADE}
     * @param blockedReason present exactly when {@code route} is not {@code TRADE}
     */
    public record AcquisitionDecision(
            AcquisitionRoute route,
            List<TradeEvaluation> rankedTradeOffers,
            TradeBlockedReason blockedReason) {

        public AcquisitionDecision {
            rankedTradeOffers = rankedTradeOffers == null ? List.of() : List.copyOf(rankedTradeOffers);
        }

        public boolean tradeChosen() {
            return route == AcquisitionRoute.TRADE;
        }

        /** The offer V2-E would attempt first. */
        public java.util.Optional<TradeEvaluation> best() {
            return rankedTradeOffers.isEmpty()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(rankedTradeOffers.get(0));
        }
    }

    private TradeDemandRegistrar() {
    }

    /**
     * Choose an acquisition route for a demand that already exists.
     *
     * @param demand supplied by an external consumer; never created or modified here
     */
    public static AcquisitionDecision decide(
            WorkDemandPolicy.MaterialDemand demand, RouteEvidence evidence) {
        if (demand == null || evidence == null) {
            return existingWork(TradeBlockedReason.NO_OFFER_EVIDENCE);
        }

        // Gate 7 first, and unconditionally. A feasible existing route is never displaced by a more
        // attractive offer, and refusing trade must never make the demand unsatisfiable.
        if (evidence.existingRouteFeasible()) {
            return existingWork(TradeBlockedReason.EXISTING_ROUTE_AVAILABLE);
        }
        // Gate 4: a candidate needs current bounded evidence, not a memory that offers once existed.
        if (!evidence.hasBoundedOfferEvidence()) {
            return existingWork(TradeBlockedReason.NO_OFFER_EVIDENCE);
        }

        List<TradeEvaluation> viable = new ArrayList<>();
        for (OfferSnapshot offer : evidence.offers()) {
            TradeEvaluationPolicy
                    .evaluate(demand, offer, evidence.externalEmeraldDeficit())
                    .evaluation()
                    .ifPresent(viable::add);
        }
        if (viable.isEmpty()) {
            return existingWork(TradeBlockedReason.NO_VIABLE_OFFER);
        }
        if (!evidence.paymentAffordable()) {
            return existingWork(TradeBlockedReason.PAYMENT_UNAVAILABLE);
        }

        // Only now, and only within TRADE, does V2-B's number decide anything (gate 6).
        viable.sort(Comparator
                .comparingDouble(TradeEvaluation::utility).reversed()
                .thenComparingInt(TradeEvaluation::offerIndex));

        return new AcquisitionDecision(AcquisitionRoute.TRADE, viable, null);
    }

    private static AcquisitionDecision existingWork(TradeBlockedReason reason) {
        return new AcquisitionDecision(AcquisitionRoute.EXISTING_WORK, List.of(), reason);
    }
}
