package com.noobk.spmscavenger.village.storage;

/**
 * D-VR-017 — diagnostic ownership label for settlement storage.
 *
 * <p>Hot-path ally enforcement uses explicit grants only ({@link StorageRaidPolicy}); this enum is
 * for {@link StorageOwnershipPolicy} / operator {@code storage get} output.
 */
public enum StorageOwnership {
    MOB_OWNED,
    EXPLICITLY_SHARED_WITH_MOB,
    VILLAGE_PUBLIC,
    FOREIGN,
    UNKNOWN
}
