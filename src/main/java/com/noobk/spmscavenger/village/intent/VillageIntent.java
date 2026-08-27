package com.noobk.spmscavenger.village.intent;

import com.noobk.spmscavenger.WorkDemandPolicy.MaterialDemandIdentity;
import com.noobk.spmscavenger.village.routing.SettlementKey;

import java.util.Objects;
import java.util.Optional;

/**
 * D-VR-091 — a transient reason and destination commitment, never cached permission.
 *
 * <p>Only {@link Kind#REQUIRED_TRADE} has a producer in V4-D. The other kinds reserve the common
 * shape without inventing new behavior.
 */
public record VillageIntent(
        Kind kind,
        SettlementKey destination,
        long openedAtTick,
        Optional<MaterialDemandIdentity> requiredTradeDemand) {

    public enum Kind {
        REQUIRED_TRADE,
        RETURN_HOME,
        VISIT_SETTLEMENT
    }

    public VillageIntent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(destination, "destination");
        requiredTradeDemand = Objects.requireNonNull(requiredTradeDemand, "requiredTradeDemand");
        if ((kind == Kind.REQUIRED_TRADE) != requiredTradeDemand.isPresent()) {
            throw new IllegalArgumentException(
                    "required trade alone must carry a material-demand identity");
        }
    }

    static VillageIntent requiredTrade(
            SettlementKey destination, long openedAtTick, MaterialDemandIdentity demand) {
        return new VillageIntent(
                Kind.REQUIRED_TRADE, destination, openedAtTick, Optional.of(demand));
    }
}
