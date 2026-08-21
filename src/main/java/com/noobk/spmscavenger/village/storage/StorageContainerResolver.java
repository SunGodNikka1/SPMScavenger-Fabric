package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Optional;

/**
 * Loaded-world truth and canonical logical identity for lootable containers.
 *
 * <p>Must not mutate {@link StoragePermissionSavedData}, load chunks, or scan POIs.
 */
public final class StorageContainerResolver {

    private StorageContainerResolver() {
    }

    public static Optional<ResolvedContainer> resolveLoaded(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !isChunkLoaded(level, pos)) {
            return Optional.empty();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!isLootableContainer(be)) {
            return Optional.empty();
        }
        BlockPos canonical = canonicalPos(level, pos);
        GlobalPos global = GlobalPos.of(level.dimension(), canonical);
        return Optional.of(new ResolvedContainer(pos.immutable(), canonical.immutable(), global));
    }

    public static ResolvedContainerFacts facts(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return new ResolvedContainerFacts(false, false, null);
        }
        if (!isChunkLoaded(level, pos)) {
            return new ResolvedContainerFacts(false, false, null);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!isLootableContainer(be)) {
            return new ResolvedContainerFacts(true, false, null);
        }
        BlockPos canonical = canonicalPos(level, pos);
        return new ResolvedContainerFacts(
                true,
                true,
                GlobalPos.of(level.dimension(), canonical));
    }

    public static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** Host-equivalent lootable predicate — pinned {@code RaidContainersGoal#isLootableContainer}. */
    public static boolean isLootableContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    public static boolean isLootableContainerState(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.hasBlockEntity();
    }

    /**
     * Canonical logical identity for stable chest topology (Gate 0 — {@code getConnectedDirection}).
     */
    public static BlockPos canonicalPos(ServerLevel level, BlockPos pos) {
        return canonicalPosFromState(level, pos, level.getBlockState(pos));
    }

    public static BlockPos canonicalPosFromState(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
            return lexicographicMin(pos, partner);
        }
        return pos.immutable();
    }

    /**
     * Pre-transition identity from {@code oldState} only — used by lifecycle before new topology settles.
     */
    public static GlobalPos canonicalGlobalFromOldState(
            ServerLevel level, BlockPos pos, BlockState oldState) {
        BlockPos canonical = canonicalPosFromState(level, pos, oldState);
        if (oldState.getBlock() instanceof ChestBlock
                && oldState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return GlobalPos.of(level.dimension(), canonical);
        }
        if (isLootableContainerBlockState(oldState)) {
            return GlobalPos.of(level.dimension(), canonical);
        }
        return GlobalPos.of(level.dimension(), pos.immutable());
    }

    static boolean isLootableContainerBlockState(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
    }

    static boolean chestTopologyChanged(BlockState oldState, BlockState newState) {
        ChestType oldType = chestType(oldState);
        ChestType newType = chestType(newState);
        if (oldType == ChestType.SINGLE && newType != ChestType.SINGLE) {
            return true;
        }
        if (oldType != ChestType.SINGLE && newType == ChestType.SINGLE) {
            return true;
        }
        if (oldType != ChestType.SINGLE && newType != ChestType.SINGLE && oldType != newType) {
            return true;
        }
        return false;
    }

    private static ChestType chestType(BlockState state) {
        if (state != null && state.getBlock() instanceof ChestBlock) {
            return state.getValue(ChestBlock.TYPE);
        }
        return ChestType.SINGLE;
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
