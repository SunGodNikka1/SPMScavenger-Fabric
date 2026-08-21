package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Pure logical-container identity for lifecycle invalidation (task-54 R1-2).
 *
 * <p>Compares canonical keys derived only from {@code state} at {@code pos} — never applies one
 * half's {@code oldState} to a partner coordinate.
 */
public final class StorageLogicalIdentity {

    public enum Kind {
        NONE,
        CHEST,
        BARREL,
        SHULKER
    }

    public record Identity(boolean supported, Kind kind, BlockPos canonicalPos) {
    }

    private StorageLogicalIdentity() {
    }

    public static Identity of(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || pos == null) {
            return unsupported(pos == null ? BlockPos.ZERO : pos);
        }
        if (state.getBlock() instanceof ChestBlock) {
            return chestIdentity(state, pos);
        }
        if (state.getBlock() instanceof BarrelBlock) {
            return new Identity(true, Kind.BARREL, pos.immutable());
        }
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return new Identity(true, Kind.SHULKER, pos.immutable());
        }
        return unsupported(pos);
    }

    /**
     * @return {@code true} when the pre-transition logical container key would change or support is
     *     lost/gained across supported kinds.
     */
    public static boolean logicalIdentityChanged(
            BlockState oldState, BlockState newState, BlockPos pos) {
        Identity oldId = of(oldState, pos);
        Identity newId = of(newState, pos);
        if (!oldId.supported() && !newId.supported()) {
            return false;
        }
        if (oldId.supported() != newId.supported()) {
            return true;
        }
        if (oldId.kind() != newId.kind()) {
            return true;
        }
        if (chestPartnerTopologyChanged(oldState, newState)) {
            return true;
        }
        return !oldId.canonicalPos().equals(newId.canonicalPos());
    }

    private static boolean chestPartnerTopologyChanged(BlockState oldState, BlockState newState) {
        if (!(oldState.getBlock() instanceof ChestBlock)
                || !(newState.getBlock() instanceof ChestBlock)) {
            return false;
        }
        ChestType oldType = oldState.getValue(ChestBlock.TYPE);
        ChestType newType = newState.getValue(ChestBlock.TYPE);
        boolean oldSingle = oldType == ChestType.SINGLE;
        boolean newSingle = newType == ChestType.SINGLE;
        if (oldSingle != newSingle) {
            return true;
        }
        if (!oldSingle) {
            return ChestBlock.getConnectedDirection(oldState) != ChestBlock.getConnectedDirection(newState);
        }
        return false;
    }

    public static BlockPos canonicalPos(BlockState state, BlockPos pos) {
        return of(state, pos).canonicalPos();
    }

    private static Identity chestIdentity(BlockState state, BlockPos pos) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) {
            return new Identity(true, Kind.CHEST, pos.immutable());
        }
        Direction connected = ChestBlock.getConnectedDirection(state);
        BlockPos partner = pos.relative(connected);
        return new Identity(true, Kind.CHEST, lexicographicMin(pos, partner));
    }

    private static Identity unsupported(BlockPos pos) {
        return new Identity(false, Kind.NONE, pos.immutable());
    }

    private static BlockPos lexicographicMin(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return a.getX() < b.getX() ? a.immutable() : b.immutable();
        }
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY() ? a.immutable() : b.immutable();
        }
        return a.getZ() <= b.getZ() ? a.immutable() : b.immutable();
    }
}
