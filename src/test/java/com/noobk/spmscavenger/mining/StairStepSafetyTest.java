package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StairStepSafetyTest {

    @Test
    void solidStoneStairPlanAcceptsWithPostBreakProjection() {
        MapBlockGetter level = new MapBlockGetter();
        BlockPos stand = new BlockPos(5, 64, 5);
        StairStepPlan plan = StairStepPlanner.planStep(stand, Direction.NORTH);
        for (BlockPos breakPos : plan.requiredBreaks()) {
            level.set(breakPos, Blocks.STONE.defaultBlockState());
        }
        level.set(plan.nextStandCell().below(), Blocks.STONE.defaultBlockState());

        assertEquals(
                StairStepSafety.Rejection.NONE,
                StairStepSafety.validatePlan(level, plan, StairStepSafety.BreakCapability.always()));
    }

    @Test
    void rejectsWhenCapabilityCannotHarvestStone() {
        MapBlockGetter level = new MapBlockGetter();
        BlockPos stand = new BlockPos(0, 64, 0);
        StairStepPlan plan = StairStepPlanner.planStep(stand, Direction.EAST);
        for (BlockPos breakPos : plan.requiredBreaks()) {
            level.set(breakPos, Blocks.STONE.defaultBlockState());
        }
        level.set(plan.nextStandCell().below(), Blocks.STONE.defaultBlockState());

        assertEquals(
                StairStepSafety.Rejection.NO_HARVEST,
                StairStepSafety.validatePlan(
                        level, plan, state -> state.isAir()));
    }

    @Test
    void postBreakGeometryTreatsBrokenCellsAsPassable() {
        MapBlockGetter level = new MapBlockGetter();
        BlockPos stand = new BlockPos(2, 64, 2);
        StairStepPlan plan = StairStepPlanner.planStep(stand, Direction.SOUTH);
        for (BlockPos breakPos : plan.requiredBreaks()) {
            level.set(breakPos, Blocks.STONE.defaultBlockState());
        }
        level.set(plan.nextStandCell().below(), Blocks.STONE.defaultBlockState());
        Set<BlockPos> broken = StairStepSafety.breakSet(plan);

        assertEquals(
                StairStepSafety.Rejection.NONE,
                StairStepSafety.validatePostBreakGeometry(level, plan, broken));
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
