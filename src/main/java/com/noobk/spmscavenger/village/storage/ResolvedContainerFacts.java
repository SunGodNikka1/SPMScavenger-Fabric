package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.GlobalPos;

/**
 * Inputs for {@link StorageOwnershipPolicy#classify} after resolver work.
 */
public record ResolvedContainerFacts(
        boolean chunkLoaded,
        boolean validLootableContainer,
        GlobalPos canonicalGlobal) {
}
