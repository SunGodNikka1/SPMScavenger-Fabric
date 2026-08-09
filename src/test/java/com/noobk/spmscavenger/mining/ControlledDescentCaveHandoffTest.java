package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledDescentCaveHandoffTest {

    @Test
    void deepUnderRimIsSubterranean() {
        ControlledDescentCaveHandoff.HeightAccess underground = uniformHeight(72, false);
        BlockPos feet = new BlockPos(0, 30, 0);

        assertTrue(ControlledDescentCaveHandoff.isSubterraneanAt(underground, feet));
    }

    @Test
    void surfaceColumnIsNotSubterranean() {
        ControlledDescentCaveHandoff.HeightAccess surface = uniformHeight(70, true);
        BlockPos feet = new BlockPos(0, 69, 0);

        assertFalse(ControlledDescentCaveHandoff.isSubterraneanAt(surface, feet));
    }

    @Test
    void openedCaveDetectsSubterraneanAheadWithoutThreeAirHeuristic() {
        ControlledDescentCaveHandoff.HeightAccess underground = uniformHeight(72, false);
        BlockPos feet = new BlockPos(0, 40, 0);

        assertTrue(ControlledDescentCaveHandoff.openedTraversableCave(
                underground, feet, Direction.NORTH, pos -> pos.getY() == 38));
    }

    @Test
    void surfaceMobDoesNotHandoffFromAirBelowFeet() {
        ControlledDescentCaveHandoff.HeightAccess surface = uniformHeight(70, true);
        BlockPos feet = new BlockPos(0, 68, 0);

        assertFalse(ControlledDescentCaveHandoff.openedTraversableCave(
                surface, feet, Direction.NORTH, pos -> pos.getY() == feet.getY()));
    }

    private static ControlledDescentCaveHandoff.HeightAccess uniformHeight(int height, boolean sky) {
        return new ControlledDescentCaveHandoff.HeightAccess() {
            @Override
            public int motionBlockingHeight(int x, int z) {
                return height;
            }

            @Override
            public boolean canSeeSky(BlockPos pos) {
                return sky;
            }
        };
    }
}
