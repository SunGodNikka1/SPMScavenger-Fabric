package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * MI-7D — one controlled-descent stair step geometry primitive.
 */
public record StairStepPlan(
        BlockPos standCell,
        BlockPos nextStandCell,
        List<BlockPos> requiredBreaks,
        int resultingHeadroom,
        int resultingDrop) {

    public StairStepPlan {
        standCell = standCell.immutable();
        nextStandCell = nextStandCell.immutable();
        requiredBreaks = List.copyOf(requiredBreaks);
    }

    public boolean hasValidGeometry() {
        return resultingHeadroom >= 2 && resultingDrop >= 1 && resultingDrop <= 1;
    }
}
