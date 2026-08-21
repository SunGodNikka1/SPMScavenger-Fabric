package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

/**
 * Loaded-world truth for one lootable logical container.
 */
public record ResolvedContainer(
        BlockPos queriedPos,
        BlockPos canonicalPos,
        GlobalPos canonicalGlobal) {
}
