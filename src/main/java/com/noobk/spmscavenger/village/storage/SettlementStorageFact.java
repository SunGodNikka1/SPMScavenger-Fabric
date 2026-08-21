package com.noobk.spmscavenger.village.storage;

/**
 * Tri-state settlement context for diagnostics — never collapse unknown into outside (D-VR-017).
 */
public enum SettlementStorageFact {
    IN_KNOWN_SETTLEMENT,
    OUTSIDE_KNOWN_SETTLEMENT,
    UNKNOWN
}
