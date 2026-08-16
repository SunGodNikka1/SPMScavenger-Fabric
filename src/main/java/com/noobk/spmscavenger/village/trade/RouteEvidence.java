package com.noobk.spmscavenger.village.trade;

import java.util.List;
import java.util.Objects;

/**
 * V2-C — everything the decision is allowed to know, as of right now.
 *
 * <h2>Why the caller supplies the offers</h2>
 *
 * The offer list arrives already bounded (gate 5). This class cannot look one up, because a policy
 * that could fetch offers would eventually fetch them for every villager in range — and
 * {@code getOffers()} lazily <b>populates</b> a villager's trades, so "looking for opportunities"
 * would silently initialise offers across a whole village. Inspection belongs to bounded
 * trade-candidate evaluation, and the boundary is enforced by this type not carrying a world.
 *
 * <h2>Why there is no cached state anywhere near this</h2>
 *
 * Evidence is passed in and thrown away. A decision is therefore a pure function of the present, so
 * "the candidate disappeared" needs no invalidation path (gate 9): the next decision simply sees
 * different evidence. Stale ownership is not handled — it is made unrepresentable.
 *
 * @param existingRouteFeasible whether gather / smelt / craft can satisfy this demand right now
 * @param offers bounded, already-inspected offers; empty means no trade evidence exists
 * @param paymentAffordable whether the mob can currently pay for at least one of those offers
 * @param externalEmeraldDeficit an emerald shortfall a named consumer already established, or
 *     {@code null}. Never synthesised here (gate 2)
 * @param sellAuthorization R4 — which backpack material may fund that deficit, from
 *     {@link SellReserveModel} and {@link SellExpendabilityPolicy}. {@code null} means no funding
 *     SELL is authorized, and every SELL offer is refused. It is carried here rather than derived in
 *     the registrar because permission is evidence about the <b>mob</b>, and the registrar is a pure
 *     decision over supplied evidence.
 */
public record RouteEvidence(
        boolean existingRouteFeasible,
        List<OfferSnapshot> offers,
        boolean paymentAffordable,
        TradeEvaluationPolicy.EmeraldDeficit externalEmeraldDeficit,
        SellAuthorization sellAuthorization) {

    public RouteEvidence {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    /** No offers means no bounded route evidence, whatever else is true (gate 4). */
    public boolean hasBoundedOfferEvidence() {
        return !offers.isEmpty();
    }

    public static RouteEvidence existingRouteOnly(boolean existingRouteFeasible) {
        return new RouteEvidence(existingRouteFeasible, List.of(), false, null, null);
    }

    public static RouteEvidence of(
            boolean existingRouteFeasible, List<OfferSnapshot> offers, boolean paymentAffordable) {
        return new RouteEvidence(existingRouteFeasible, Objects.requireNonNull(offers),
                paymentAffordable, null, null);
    }
}
