package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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

        // Headroom: the cell above the destination is broken by this step, so the constraint is on
        // the cell above *that* - the corridor must not open directly under an unsupported column.
        StairStepSafety.Rejection ceiling =
                StairStepSafety.validateBreakHazards(level, destination.above(2));
        if (ceiling == StairStepSafety.Rejection.FALLING_BLOCK
                || ceiling == StairStepSafety.Rejection.LIQUID) {
            return ceiling;
        }

        return StairStepSafety.Rejection.NONE;
    }
}
