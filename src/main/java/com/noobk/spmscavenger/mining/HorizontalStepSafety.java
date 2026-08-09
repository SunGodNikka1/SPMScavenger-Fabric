package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * D-MIW-TS3 — safety for a 1x2 horizontal corridor step.
 *
 * <p>Reuses {@link StairStepSafety#validateBreakHazards} and
 * {@link StairStepSafety.BreakCapability} for per-block hazards (liquid, falling block above,
 * unbreakable, harvest capability) and adds only the post-break checks that differ for a corridor:
 * 2-high passability at the destination and solid footing under it.
 *
 * <p>It does <b>not</b> call {@link StairStepSafety#validatePlan}, whose post-break geometry assumes
 * a one-block drop. A corridor that inherited {@code DROP_TOO_DEEP} logic would reject flat ground.
 */
public final class HorizontalStepSafety {

    private HorizontalStepSafety() {
    }

    public static StairStepSafety.Rejection validatePlan(
            BlockGetter level, StairStepPlan plan, StairStepSafety.BreakCapability capability) {

        if (plan == null || capability == null) {
            return StairStepSafety.Rejection.NO_FOOTING;
        }

        for (BlockPos cell : plan.requiredBreaks()) {
            StairStepSafety.Rejection hazard = StairStepSafety.validateBreakHazards(level, cell);
            if (hazard != StairStepSafety.Rejection.NONE) {
                return hazard;
            }
            if (!capability.canHarvest(level.getBlockState(cell))) {
                return StairStepSafety.Rejection.NO_HARVEST;
            }
        }

        BlockPos destination = plan.nextStandCell();

        // Footing: a corridor stays level, so the block under the destination must hold the mob.
        // Checked after hazards so a lava pocket ahead reports LIQUID rather than NO_FOOTING - the
        // caller reacts differently to "dangerous" than to "step down".
        BlockState floor = level.getBlockState(destination.below());
        if (floor.isAir() || !floor.isSolidRender(level, destination.below())) {
            return StairStepSafety.Rejection.NO_FOOTING;
        }

        return validateOverhead(level, destination.above(2));
    }

    /**
     * TS3-M1 — the ceiling directly over the opened headspace, and nothing above it.
     *
     * <p>{@link StairStepSafety#validateBreakHazards} inspects {@code pos.above()} for falling
     * blocks, which is correct for a cell being <em>excavated</em> and wrong for a cell being
     * <em>left in place</em>. Applying it to the ceiling asked about the block above the ceiling, so
     * gravel resting on solid stone — which cannot fall into the corridor — rejected the step.
     * Geometry A would have stopped at random points under perfectly safe rock.
     *
     * <p>What actually matters here is the overhead cell itself: fluid that would pour into the
     * opened headspace, or unstable material that would drop into it once the head cell is gone.
     * "Breaking this block drops its neighbour" is already covered for the two cells the step
     * excavates.
     */
    private static StairStepSafety.Rejection validateOverhead(
            BlockGetter level, BlockPos overhead) {
        if (!level.getFluidState(overhead).isEmpty()) {
            return StairStepSafety.Rejection.LIQUID;
        }
        BlockState state = level.getBlockState(overhead);
        if (state.is(Blocks.LAVA) || state.is(Blocks.WATER)) {
            return StairStepSafety.Rejection.LIQUID;
        }
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
            return StairStepSafety.Rejection.FALLING_BLOCK;
        }
        return StairStepSafety.Rejection.NONE;
    }
}
