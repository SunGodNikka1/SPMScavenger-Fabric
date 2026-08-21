package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
        return StorageLogicalIdentity.canonicalPos(state, pos);
    }

    /**
     * Pre-transition identity from {@code oldState} only — used by lifecycle before new topology settles.
     */
    public static GlobalPos canonicalGlobalFromOldState(
            ServerLevel level, BlockPos pos, BlockState oldState) {
        return GlobalPos.of(level.dimension(), StorageLogicalIdentity.canonicalPos(oldState, pos));
    }

    static boolean isLootableContainerBlockState(BlockState state) {
        return StorageLogicalIdentity.of(state, BlockPos.ZERO).supported();
    }
}
