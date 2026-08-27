package com.noobk.spmscavenger.opinion;

import java.util.Map;
import java.util.Objects;

/**
 * V4-B — immutable, already-resolved Opinion inputs for one settlement-ranking pass.
 *
 * <p>This is deliberately a value snapshot rather than a service locator. It cannot load chunks,
 * discover villages or traders, create memories, or mutate the source Opinion state. The map is
 * geographic Place memory, not a persisted settlement identity: the caller may reuse the same
 * snapshot for several villages and each request resolves the village's current anchor chunk.
 */
public record SettlementOpinionContext(Map<Long, Float> placePreferences) {

    public SettlementOpinionContext {
        placePreferences = Map.copyOf(Objects.requireNonNull(
                placePreferences, "placePreferences"));
    }

    /** Captures existing Place truth without creating or updating any Opinion entry. */
    public static SettlementOpinionContext from(PlaceOpinionMemory places) {
        Objects.requireNonNull(places, "places");
        return new SettlementOpinionContext(places.captureSnapshot());
    }

    /** Neutral context for callers that have no already-resolved Place memory. */
    public static SettlementOpinionContext neutral() {
        return new SettlementOpinionContext(Map.of());
    }
}
