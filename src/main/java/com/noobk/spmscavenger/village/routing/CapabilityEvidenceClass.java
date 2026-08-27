package com.noobk.spmscavenger.village.routing;

/** D-VR-092 — epistemic class for a remembered output, never live market feasibility. */
public enum CapabilityEvidenceClass {
    POSITIVE_HINT(0),
    UNKNOWN(1);

    private final int rank;

    CapabilityEvidenceClass(int rank) {
        this.rank = rank;
    }

    int rank() {
        return rank;
    }
}
