package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14 MAIBS re-pass — the five perception cases the R2 family must survive before the control
 * plane (MI-14C) is built on top of it.
 *
 * <p>Each case is stated as physical situation first, expectation second. A staircase completes
 * step S0 to S1; the question is always "did that step break into somewhere the mob can go".
 */
class CaveOpeningMaibsTest {

    private static final int SURFACE = 70;
    private static final BlockPos S0 = new BlockPos(0, 50, 0);
    private static final Direction HEADING = Direction.EAST;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class World implements ControlledDescentCaveHandoff.HeightAccess {
        private final Set<BlockPos> standable = new HashSet<>();
        private final Set<BlockPos> passable = new HashSet<>();

        World open(BlockPos... cells) {
            for (BlockPos cell : cells) {
                standable.add(cell.immutable());
                passable.add(cell.immutable());
            }
            return this;
        }

        World air(BlockPos... cells) {
            for (BlockPos cell : cells) {
                passable.add(cell.immutable());
            }
            return this;
        }

        @Override
        public int motionBlockingHeight(int x, int z) {
            return SURFACE;
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return pos.getY() >= SURFACE;
        }
    }

    /** The staircase as actually dug: a 3-high column at each stand reached so far. */
    private static World dugStaircase(World world, StairStepPlan completed) {
        BlockPos stand = completed.standCell();
        world.open(stand, stand.above(), stand.above(2));
        BlockPos next = completed.nextStandCell();
        world.open(next, next.above(), next.above(2));
        return world;
    }

    private static Optional<CaveOpening> detect(World world, StairStepPlan completed) {
        Predicate<BlockPos> passable = pos -> world.passable.contains(pos.immutable());
        Predicate<BlockPos> standable = pos -> world.standable.contains(pos.immutable());
        return ControlledDescentCaveHandoff.findOpenedCave(
                world, completed,
                ControlledDescentCaveHandoff.selfCorridor(completed.nextStandCell(), HEADING),
                passable, standable);
    }

    // R2-C1 — nothing but the mob's own staircase exists.
    @Test
    void r2c1_selfCorridorOnlyIsNotAnOpening() {
        StairStepPlan completed = StairStepPlanner.planStep(S0, HEADING);
        World world = dugStaircase(new World(), completed);

        assertTrue(detect(world, completed).isEmpty(),
                "R2-C1: a mob standing in its own tunnel has discovered nothing");
    }

    // R2-C2 — a cave exists ahead but a solid wall still separates it.
    @Test
    void r2c2_caveBehindTheIntactFutureWallIsNotAnOpening() {
        StairStepPlan completed = StairStepPlanner.planStep(S0, HEADING);
        World world = dugStaircase(new World(), completed);
        world.open(new BlockPos(3, 49, 0));   // (2,49,0) left solid as the separating wall

        assertTrue(detect(world, completed).isEmpty(),
                "R2-C2: geographically close, topologically sealed");
    }

    // R2-C3 — the completed excavation touches natural cave air.
    @Test
    void r2c3_caveTouchingTheCompletedExcavationIsAnOpening() {
        StairStepPlan completed = StairStepPlanner.planStep(S0, HEADING);
        World world = dugStaircase(new World(), completed);
        BlockPos lateral = new BlockPos(1, 49, 1);
        world.open(lateral).air(lateral.above());

        Optional<CaveOpening> opening = detect(world, completed);
        assertTrue(opening.isPresent(), "R2-C3: air against the cut wall is a breakthrough");
        assertTrue(opening.get().isSubterranean());
    }

    // R2-C4 — the cave is ALREADY open directly ahead; no further digging is needed to enter it.
    @Test
    void r2c4_naturalCaveAlreadyOpenDirectlyAheadIsAnOpening() {
        StairStepPlan completed = StairStepPlanner.planStep(S0, HEADING);
        World world = dugStaircase(new World(), completed);

        // The cells the planner *would* dig next happen to be natural cave air already.
        StairStepPlan future = StairStepPlanner.planStep(completed.nextStandCell(), HEADING);
        world.open(future.nextStandCell());
        for (BlockPos cell : future.requiredBreaks()) {
            world.air(cell);
        }

        assertTrue(detect(world, completed).isPresent(),
                "R2-C4: walking forward into existing cave air is exactly a breakthrough - it must "
                        + "not require digging cells that are already open");
    }

    // R2-C5 — connected air, but not connected mob-sized space.
    @Test
    void r2c5_connectionThroughAOneHighSlitIsNotTraversable() {
        StairStepPlan completed = StairStepPlanner.planStep(S0, HEADING);
        World world = dugStaircase(new World(), completed);

        world.air(new BlockPos(2, 49, 0));            // slit: (2,50,0) stays solid
        world.open(new BlockPos(3, 49, 0));           // chamber floor beyond it
        world.air(new BlockPos(3, 50, 0));            // chamber has real headroom

        assertFalse(detect(world, completed).isPresent(),
                "R2-C5: a two-block-tall mob cannot follow a one-block-high connection");
    }
}
