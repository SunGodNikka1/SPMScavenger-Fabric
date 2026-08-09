package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * D-MIW-TS3 — one 1x2 horizontal corridor step (Geometry A).
 *
 * <p>Deliberately not a variant of {@link StairStepPlanner}: a stair step drops one block and cuts a
 * 3-high column at its destination, and inheriting that geometry is how a corridor would acquire a
 * staircase's drop checks. Only the per-block hazard primitives are shared
 * ({@link StairStepSafety#validateBreakHazards}), never the plan validator.
 */
public final class HorizontalStepPlanner {

    private HorizontalStepPlanner() {
    }

    /**
     * Plans one step forward at the same height.
     *
     * <p>Break order is headroom then body, matching the stair planner: cutting the upper cell first
     * means a falling-block hazard above reveals itself before the mob is standing in the gap.
     */
    public static StairStepPlan planStep(BlockPos standCell, Direction heading) {
        BlockPos forwardFeet = standCell.relative(heading);
        List<BlockPos> breaks = new ArrayList<>(2);
        breaks.add(forwardFeet.above());
        breaks.add(forwardFeet);
        return new StairStepPlan(standCell, forwardFeet, breaks, 2, 0);
    }
}
