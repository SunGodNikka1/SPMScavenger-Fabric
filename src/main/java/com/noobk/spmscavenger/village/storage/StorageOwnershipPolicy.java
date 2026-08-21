package com.noobk.spmscavenger.village.storage;

import java.util.UUID;

/**
 * Pure diagnostics — never mutates grants or participates in ally enforcement.
 */
public final class StorageOwnershipPolicy {

    private StorageOwnershipPolicy() {
    }

    public static StorageOwnership classify(
            ResolvedContainerFacts facts,
            SettlementStorageFact settlement,
            UUID mobId,
            GrantSnapshot grants) {
        if (facts == null || !facts.chunkLoaded()) {
            return StorageOwnership.UNKNOWN;
        }
        if (!facts.validLootableContainer()) {
            return StorageOwnership.UNKNOWN;
        }
        if (grants != null && mobId != null && facts.canonicalGlobal() != null) {
            if (grants.hasOwner(facts.canonicalGlobal(), mobId)) {
                return StorageOwnership.MOB_OWNED;
            }
            if (grants.isSharedWith(facts.canonicalGlobal(), mobId)) {
                return StorageOwnership.EXPLICITLY_SHARED_WITH_MOB;
            }
        }
        if (settlement == SettlementStorageFact.IN_KNOWN_SETTLEMENT) {
            return StorageOwnership.VILLAGE_PUBLIC;
        }
        if (settlement == SettlementStorageFact.OUTSIDE_KNOWN_SETTLEMENT) {
            return StorageOwnership.FOREIGN;
        }
        return StorageOwnership.UNKNOWN;
    }
}
