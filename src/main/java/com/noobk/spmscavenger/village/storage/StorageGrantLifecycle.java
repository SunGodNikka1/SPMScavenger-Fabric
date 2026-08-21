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
        invalidatePreTransitionIdentity(level, pos, oldState, newState);
    }

    static void invalidatePreTransitionIdentity(
            ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(level.getServer());
        if (data == null) {
            return;
        }

        boolean oldLootable = StorageContainerResolver.isLootableContainerBlockState(oldState);
        boolean newLootable = StorageContainerResolver.isLootableContainerBlockState(newState);
        boolean topologyChanged = StorageContainerResolver.chestTopologyChanged(oldState, newState);

        if (oldLootable) {
            GlobalPos oldKey = StorageContainerResolver.canonicalGlobalFromOldState(level, pos, oldState);
            data.invalidateAt(oldKey);
            if (topologyChanged) {
                invalidateOldDoublePartner(level, pos, oldState, data);
            }
        } else if (!newLootable && oldState.hasBlockEntity()) {
            GlobalPos posKey = GlobalPos.of(level.dimension(), pos.immutable());
            data.invalidateAt(posKey);
        }
    }

    private static void invalidateOldDoublePartner(
            ServerLevel level, BlockPos pos, BlockState oldState,
            StoragePermissionSavedData data) {
        if (!(oldState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            return;
        }
        if (oldState.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            return;
        }
        BlockPos partner = pos.relative(
                net.minecraft.world.level.block.ChestBlock.getConnectedDirection(oldState));
        GlobalPos partnerKey = GlobalPos.of(
                level.dimension(),
                StorageContainerResolver.canonicalPosFromState(level, partner, oldState));
        data.invalidateAt(partnerKey);
    }
}
