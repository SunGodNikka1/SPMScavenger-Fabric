package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StairStepPlannerTest {

    @Test
    void plansOneBlockDownForwardStep() {
        BlockPos stand = new BlockPos(5, 64, 5);
        StairStepPlan plan = StairStepPlanner.planStep(stand, Direction.NORTH);
        assertEquals(new BlockPos(5, 63, 4), plan.nextStandCell());
        assertEquals(3, plan.requiredBreaks().size());
        assertTrue(plan.hasValidGeometry());
        assertEquals(2, plan.resultingHeadroom());
        assertEquals(1, plan.resultingDrop());
    }

    @Test
    void breakOrderIsHeadroomBeforeBodyBeforeFloor() {
        BlockPos stand = new BlockPos(0, 64, 0);
        StairStepPlan plan = StairStepPlanner.planStep(stand, Direction.EAST);
        assertEquals(new BlockPos(1, 65, 0), plan.requiredBreaks().get(0));
        assertEquals(new BlockPos(1, 64, 0), plan.requiredBreaks().get(1));
        assertEquals(new BlockPos(1, 63, 0), plan.requiredBreaks().get(2));
    }
}
