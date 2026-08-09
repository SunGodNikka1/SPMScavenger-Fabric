package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * MI-7D — plans ordered breaks for a single 1×2 downward stair step.
 */
public final class StairStepPlanner {

    private StairStepPlanner() {
    }

    /**
     * Plans one step forward and one block down along {@code heading}.
     *
     * <p>Break order: destination headroom, destination body, destination floor — per RFC executor.
     */
    public static StairStepPlan planStep(BlockPos standCell, Direction heading) {
        BlockPos forwardFeet = standCell.relative(heading);
        BlockPos nextStand = forwardFeet.below();
        List<BlockPos> breaks = new ArrayList<>(3);
        breaks.add(forwardFeet.above());
        breaks.add(forwardFeet);
        breaks.add(nextStand);
        int drop = standCell.getY() - nextStand.getY();
        return new StairStepPlan(standCell, nextStand, breaks, 2, drop);
    }
}
