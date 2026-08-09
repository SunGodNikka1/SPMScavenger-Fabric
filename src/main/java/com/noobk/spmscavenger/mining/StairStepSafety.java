package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.ToolBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashSet;
import java.util.Set;

/**
 * MI-7D / MI-7R — minimum pre-break safety gate (full MI-18 extends later).
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

    /** Whether the mob can harvest a block with any owned tool (MI-7R R2). */
    @FunctionalInterface
    public interface BreakCapability {
        boolean canHarvest(BlockState state);

        static BreakCapability fromMob(Mob mob) {
            return state -> state.isAir() || ToolBox.ownsToolFor(mob, state);
        }

        static BreakCapability fromTool(ItemStack tool) {
            return state -> {
                if (state.isAir()) {
                    return true;
                }
                return tool.isEmpty() || tool.isCorrectToolForDrops(state);
            };
        }

        static BreakCapability always() {
            return state -> true;
        }
    }

    private StairStepSafety() {
    }

    public static Rejection validatePlan(Level level, StairStepPlan plan, Mob mob) {
        return validatePlan(level, plan, BreakCapability.fromMob(mob));
    }

    /**
     * Validates hazards on blocks to break, harvest capability, then <b>post-break</b> geometry
     * (MI-7R R1).
     */
    public static Rejection validatePlan(Level level, StairStepPlan plan, BreakCapability capability) {
        return validatePlan((BlockGetter) level, plan, capability);
    }

    /**
     * Validates hazards on blocks to break, harvest capability, then <b>post-break</b> geometry
     * (MI-7R R1).
     */
    public static Rejection validatePlan(BlockGetter level, StairStepPlan plan, BreakCapability capability) {
        if (!plan.hasValidGeometry()) {
            return Rejection.NO_HEADROOM;
        }
        Set<BlockPos> broken = breakSet(plan);
        for (BlockPos breakPos : plan.requiredBreaks()) {
            BlockState state = level.getBlockState(breakPos);
            if (state.isAir()) {
                continue;
            }
            Rejection hazard = validateBreakHazards(level, breakPos);
            if (hazard != Rejection.NONE) {
                return hazard;
            }
            if (!capability.canHarvest(state)) {
                return Rejection.NO_HARVEST;
            }
        }
        return validatePostBreakGeometry(level, plan, broken);
    }

    /** Per-block hazard check at break time (after equip). */
    public static Rejection validateBreakHazards(BlockGetter level, BlockPos pos) {
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
        return Rejection.NONE;
    }

    /** @deprecated use {@link #validatePlan(Level, StairStepPlan, BreakCapability)} */
    @Deprecated
    public static Rejection validateBreak(Level level, BlockPos pos, ItemStack tool) {
        Rejection hazard = validateBreakHazards(level, pos);
        if (hazard != Rejection.NONE) {
            return hazard;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !tool.isEmpty() && !tool.isCorrectToolForDrops(state)) {
            return Rejection.NO_HARVEST;
        }
        return Rejection.NONE;
    }

    static Rejection validatePostBreakGeometry(BlockGetter level, StairStepPlan plan, Set<BlockPos> broken) {
        BlockPos feet = plan.nextStandCell();
        if (!wouldBePassable(level, feet, broken)) {
            return Rejection.NO_HEADROOM;
        }
        if (!wouldBePassable(level, feet.above(), broken)) {
            return Rejection.NO_HEADROOM;
        }
        if (!hasFooting(level, feet)) {
            return Rejection.NO_FOOTING;
        }
        if (unsupportedDrop(level, feet) > 1) {
            return Rejection.DROP_TOO_DEEP;
        }
        return Rejection.NONE;
    }

    static Set<BlockPos> breakSet(StairStepPlan plan) {
        return new HashSet<>(plan.requiredBreaks());
    }

    static boolean wouldBePassable(BlockGetter level, BlockPos pos, Set<BlockPos> broken) {
        if (broken.contains(pos)) {
            return true;
        }
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    static boolean hasFooting(BlockGetter level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
    }

    static int unsupportedDrop(BlockGetter level, BlockPos feet) {
        int drop = 0;
        BlockPos cursor = feet.below();
        while (drop < 4 && level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
            drop++;
            cursor = cursor.below();
        }
        return drop;
    }
}
