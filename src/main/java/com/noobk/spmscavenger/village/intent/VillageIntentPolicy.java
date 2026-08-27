package com.noobk.spmscavenger.village.intent;

import com.noobk.spmscavenger.WorkDemandPolicy.MaterialDemand;
import com.noobk.spmscavenger.village.routing.SettlementDestinationRanker;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility.ExistingRouteStatus;

import java.util.Objects;
import java.util.Optional;

/** D-VR-091 pure opening and legitimacy revalidation. Owns no external fact or permission. */
public final class VillageIntentPolicy {

    private VillageIntentPolicy() {
    }

    public static Optional<VillageIntent> openRequiredTrade(
            VillageIntentFacts current,
            SettlementDestinationRanker.Selection selected,
            long now) {
        if (current == null || selected == null || current.interrupted()) {
            return Optional.empty();
        }
        Optional<MaterialDemand> demand = current.liveDemand();
        if (demand.isEmpty()
                || current.existingRouteStatus() != ExistingRouteStatus.INFEASIBLE
                || !current.compatibleRememberedDestinations()
                        .contains(selected.facts().key())) {
            return Optional.empty();
        }
        return Optional.of(VillageIntent.requiredTrade(
                selected.facts().key(), now, demand.get().identity()));
    }

    public static VillageIntentEvaluation revalidate(
            VillageIntent intent, VillageIntentFacts current) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(current, "current");
        if (intent.kind() != VillageIntent.Kind.REQUIRED_TRADE) {
            throw new IllegalArgumentException("V4-D has no lifecycle producer for " + intent.kind());
        }
        Optional<MaterialDemand> demand = current.liveDemand();
        if (demand.isEmpty()) {
            return VillageIntentEvaluation.closed(VillageIntentEvaluation.Cause.DEMAND_GONE);
        }
        if (!intent.requiredTradeDemand().orElseThrow().equals(demand.get().identity())) {
            return VillageIntentEvaluation.closed(VillageIntentEvaluation.Cause.DEMAND_CHANGED);
        }
        if (current.existingRouteStatus() != ExistingRouteStatus.INFEASIBLE) {
            return VillageIntentEvaluation.closed(
                    VillageIntentEvaluation.Cause.ROUTE_JUSTIFICATION_LOST);
        }
        if (!current.compatibleRememberedDestinations().contains(intent.destination())) {
            return VillageIntentEvaluation.closed(VillageIntentEvaluation.Cause.DESTINATION_INVALID);
        }
        return current.interrupted()
                ? VillageIntentEvaluation.interrupted()
                : VillageIntentEvaluation.active();
    }
}
