package com.noobk.spmscavenger;

/**
 * Craftable tool material tiers for scavenger progression. Ordered for comparison only —
 * {@link ToolTierPolicy} maps concrete {@link net.minecraft.world.item.Item}s to these values.
 */
public enum ToolTier {
    NONE,
    WOOD,
    STONE,
    IRON,
    DIAMOND
}
