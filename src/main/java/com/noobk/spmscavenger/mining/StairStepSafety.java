package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * MI-7D — minimum pre-break safety gate (full MI-18 extends later).
 */
public final class StairStepSafety {

    public enum Rejection {
        NONE,
        LIQUID,
        FALLING_BLOCK,
        DROP_TOO_DEEP,
        UNBREAKABLE,
        NO_HARVEST,
        NO_HEADROOM,
        NO_FOOTING
    }

    private StairStepSafety() {
    }

    public static Rejection validatePlan(Level level, StairStepPlan plan, ItemStack tool) {
        if (!plan.hasValidGeometry()) {
            return Rejection.NO_HEADROOM;
        }
        for (BlockPos breakPos : plan.requiredBreaks()) {
            Rejection breakReject = validateBreak(level, breakPos, tool);
            if (breakReject != Rejection.NONE) {
                return breakReject;
            }
        }
        if (!hasFooting(level, plan.nextStandCell())) {
            return Rejection.NO_FOOTING;
        }
        if (!hasHeadroom(level, plan.nextStandCell())) {
            return Rejection.NO_HEADROOM;
        }
        int unsupported = unsupportedDrop(level, plan.nextStandCell());
        if (unsupported > 1) {
            return Rejection.DROP_TOO_DEEP;
        }
        return Rejection.NONE;
    }

    public static Rejection validateBreak(Level level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return Rejection.NONE;
        }
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty() || state.is(Blocks.LAVA) || state.is(Blocks.WATER)) {
            return Rejection.LIQUID;
        }
        BlockState above = level.getBlockState(pos.above());
        if (above.is(Blocks.GRAVEL) || above.is(Blocks.SAND) || above.is(Blocks.RED_SAND)) {
            return Rejection.FALLING_BLOCK;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0f) {
            return Rejection.UNBREAKABLE;
        }
        if (!tool.isEmpty() && !tool.isCorrectToolForDrops(state)) {
            return Rejection.NO_HARVEST;
        }
        return Rejection.NONE;
    }

    static boolean hasFooting(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
    }

    static boolean hasHeadroom(Level level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    static int unsupportedDrop(Level level, BlockPos feet) {
        int drop = 0;
        BlockPos cursor = feet.below();
        while (drop < 4 && level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
            drop++;
            cursor = cursor.below();
        }
        return drop;
    }
}
