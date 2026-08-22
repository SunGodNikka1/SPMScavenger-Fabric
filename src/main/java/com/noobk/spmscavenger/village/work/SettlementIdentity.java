package com.noobk.spmscavenger.village.work;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Transient cache key for settlement-work facts — one remembered {@code KnownVillage} anchor.
 *
 * <p>Stable only while the anchor is canonical; supersede invalidates the old identity (D-VR-083-A1 /
 * task-56 Gate 0).
 */
public record SettlementIdentity(ResourceKey<Level> dimension, BlockPos anchor) {

    public SettlementIdentity {
        Objects.requireNonNull(dimension, "dimension");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
    }

    public static SettlementIdentity of(ResourceKey<Level> dimension, BlockPos anchor) {
        return new SettlementIdentity(dimension, anchor);
    }
}
