package com.noobk.spmscavenger.village.intent;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility.ExistingRouteStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Already-resolved live facts used to open or revalidate one required-trade commitment. */
public record VillageIntentFacts(
        Optional<WorkDemandPolicy.MaterialDemand> liveDemand,
        ExistingRouteStatus existingRouteStatus,
        Set<SettlementKey> compatibleRememberedDestinations,
        boolean interrupted) {

    public VillageIntentFacts {
        liveDemand = Objects.requireNonNull(liveDemand, "liveDemand");
        existingRouteStatus = Objects.requireNonNull(existingRouteStatus, "existingRouteStatus");
        compatibleRememberedDestinations = Set.copyOf(Objects.requireNonNull(
                compatibleRememberedDestinations, "compatibleRememberedDestinations"));
    }

    public static VillageIntentFacts noDemand(
            ExistingRouteStatus status,
            Set<SettlementKey> compatibleRememberedDestinations,
            boolean interrupted) {
        return new VillageIntentFacts(
                Optional.empty(), status, compatibleRememberedDestinations, interrupted);
    }
}
