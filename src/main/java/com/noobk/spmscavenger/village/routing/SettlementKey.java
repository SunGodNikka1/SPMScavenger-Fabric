package com.noobk.spmscavenger.village.routing;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Stable factual settlement identity for deterministic ranking and transient attempt evidence. */
public record SettlementKey(ResourceKey<Level> dimension, BlockPos anchor)
        implements Comparable<SettlementKey> {

    public SettlementKey {
        dimension = Objects.requireNonNull(dimension, "dimension");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
    }

    @Override
    public int compareTo(SettlementKey other) {
        int dimensionOrder = dimension.location().toString()
                .compareTo(other.dimension.location().toString());
        if (dimensionOrder != 0) {
            return dimensionOrder;
        }
        int xOrder = Integer.compare(anchor.getX(), other.anchor.getX());
        if (xOrder != 0) {
            return xOrder;
        }
        int yOrder = Integer.compare(anchor.getY(), other.anchor.getY());
        return yOrder != 0 ? yOrder : Integer.compare(anchor.getZ(), other.anchor.getZ());
    }
}
