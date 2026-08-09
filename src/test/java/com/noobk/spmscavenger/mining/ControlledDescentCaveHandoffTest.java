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

    /**
     * MI-14-R2b/R2c changed this contract deliberately. A standable floor two blocks below and
     * ahead used to be an opening; it no longer is unless air actually connects the excavation to
     * it. That is the "cave behind unbroken stone" case, and it was a false handoff.
     */
    @Test
    void aDisconnectedFloorAheadIsNoLongerAnOpening() {
        ControlledDescentCaveHandoff.HeightAccess underground = uniformHeight(72, false);
        BlockPos feet = new BlockPos(0, 40, 0);

        assertFalse(ControlledDescentCaveHandoff.openedTraversableCave(
                        underground, feet, Direction.NORTH, pos -> pos.getY() == 38),
                "a floor with no air path from the excavation is not something the mob broke into");
    }

    @Test
    void openedCaveDetectsSubterraneanAheadOnceAirConnectsToIt() {
        ControlledDescentCaveHandoff.HeightAccess underground = uniformHeight(72, false);
        BlockPos feet = new BlockPos(0, 40, 0);

        assertTrue(ControlledDescentCaveHandoff.openedTraversableCave(
                        underground, feet, Direction.NORTH,
                        pos -> pos.getY() >= 38 && pos.getY() <= 41),
                "connected air from the cells the step opened down to the cave floor");
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
