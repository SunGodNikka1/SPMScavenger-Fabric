package com.noobk.spmscavenger.village.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * All grant invalidation and stale-row deletion (D-VR-081).
 *
 * <p>Wired from {@code ServerLevel.onBlockStateChange} — not from raid guard ticks.
 */
public final class StorageGrantLifecycle {

    private StorageGrantLifecycle() {
    }

    public static void onBlockStateChange(
            ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level == null || pos == null || oldState == null || newState == null) {
            return;
        }
        if (oldState == newState) {
            return;
        }
        invalidateIfIdentityChanged(level, pos, oldState, newState);
    }

    static void invalidateIfIdentityChanged(
            ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!StorageLogicalIdentity.logicalIdentityChanged(oldState, newState, pos)) {
            return;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(level.getServer());
        if (data == null) {
            return;
        }
        StorageLogicalIdentity.Identity oldId = StorageLogicalIdentity.of(oldState, pos);
        if (!oldId.supported()) {
            return;
        }
        GlobalPos oldKey = GlobalPos.of(level.dimension(), oldId.canonicalPos());
        data.invalidateAt(oldKey);
    }

    /** Test seam — invalidate using an explicit store without server plumbing. */
    static void invalidateIfIdentityChanged(
            StoragePermissionSavedData data,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            BlockPos pos,
            BlockState oldState,
            BlockState newState) {
        if (data == null || dimension == null || pos == null || oldState == null || newState == null) {
            return;
        }
        if (!StorageLogicalIdentity.logicalIdentityChanged(oldState, newState, pos)) {
            return;
        }
        StorageLogicalIdentity.Identity oldId = StorageLogicalIdentity.of(oldState, pos);
        if (!oldId.supported()) {
            return;
        }
        data.invalidateAt(GlobalPos.of(dimension, oldId.canonicalPos()));
    }
}
