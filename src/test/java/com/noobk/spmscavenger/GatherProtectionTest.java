package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatherProtectionTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stoneInHorizontalWallRunIsNotGatherable() {
        MapBlockGetter level = new MapBlockGetter();
        BlockPos middle = new BlockPos(1, 64, 0);
        level.set(middle.west(), Blocks.STONE.defaultBlockState());
        level.set(middle, Blocks.STONE.defaultBlockState());
        level.set(middle.east(), Blocks.STONE.defaultBlockState());
        level.set(middle.above(), Blocks.AIR.defaultBlockState());
        level.set(middle.below(), Blocks.GRAVEL.defaultBlockState());

        ScavengerConfig cfg = new ScavengerConfig();
        cfg.protectPlayerBuilds = true;

        assertFalse(GatherProtection.isGatherableStone(level, middle, cfg));
    }

    @Test
    void exposedSurfaceStoneWithoutBuildNearbyIsGatherable() {
        MapBlockGetter level = new MapBlockGetter();
        BlockPos stone = new BlockPos(0, 64, 0);
        level.set(stone, Blocks.STONE.defaultBlockState());
        level.set(stone.above(), Blocks.AIR.defaultBlockState());
        level.set(stone.below(), Blocks.GRAVEL.defaultBlockState());
        level.set(stone.north(), Blocks.GRAVEL.defaultBlockState());
        level.set(stone.south(), Blocks.GRAVEL.defaultBlockState());
        level.set(stone.east(), Blocks.GRAVEL.defaultBlockState());
        level.set(stone.west(), Blocks.GRAVEL.defaultBlockState());

        ScavengerConfig cfg = new ScavengerConfig();
        cfg.protectPlayerBuilds = true;

        assertTrue(GatherProtection.isGatherableStone(level, stone, cfg));
    }

    static final class MapBlockGetter implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void set(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getLightEmission(BlockPos pos) {
            return getBlockState(pos).getLightEmission();
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public int getHeight() {
            return 384;
        }
    }
}
