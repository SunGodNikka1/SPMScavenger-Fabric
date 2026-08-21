package com.noobk.spmscavenger.village.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minimal block-truth surface for bounded crop scans (task-55 R1-1).
 */
public interface CropWorldView {

    boolean isLoaded(BlockPos pos);

    BlockState getBlockState(BlockPos pos);

    static CropWorldView from(ServerLevel level) {
        return new CropWorldView() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return level.getBlockState(pos);
            }
        };
    }
}
