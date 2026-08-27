package com.noobk.spmscavenger.village.routing;

import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.SettlementTuning;

import java.util.Objects;

/** Immutable remembered facts for one destination candidate. */
public record SettlementDestinationFacts(
        SettlementKey key,
        KnownVillage village,
        CapabilityEvidenceClass capabilityEvidence,
        boolean home,
        int familiarity) {

    public SettlementDestinationFacts {
        key = Objects.requireNonNull(key, "key");
        village = Objects.requireNonNull(village, "village");
        capabilityEvidence = Objects.requireNonNull(capabilityEvidence, "capabilityEvidence");
        if (!key.anchor().equals(village.anchor())) {
            throw new IllegalArgumentException("settlement key and village anchor differ");
        }
        if (familiarity < 0 || familiarity > SettlementTuning.MAX_FAMILIARITY) {
            throw new IllegalArgumentException("familiarity outside established bounds: " + familiarity);
        }
    }
}
