package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.CaveContextPolicy;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14-R2 — a discovery must be a <b>place</b>, not a state.
 *
 * <p>The replaced implementation returned {@code true} as soon as the mob was subterranean. A
 * staircase is subterranean by construction, so it fired on the mob's own corridor at eight blocks
 * of rim depth and handed downstream an unresolved target. The fourth scenario below is the
 * conceptual one: <em>cave-like context without a cave opportunity is valid and must produce
 * nothing</em>.
 */
class CaveOpeningEvidenceTest {

    private static final int SURFACE = 70;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Flat hill at {@link #SURFACE}; only explicitly-added cells are standable. */
    private static final class World implements ControlledDescentCaveHandoff.HeightAccess {
        private final Set<BlockPos> standable = new HashSet<>();

        World open(BlockPos... cells) {
            for (BlockPos cell : cells) {
                standable.add(cell.immutable());
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

        Predicate<BlockPos> standablePredicate() {
            return pos -> standable.contains(pos.immutable());
        }
    }

    // ---- 1. SOLID HILL ----

    @Test
    void mustNotHappen_solidHillReportsACave() {
        World world = new World();          // nothing standable anywhere
        BlockPos feet = new BlockPos(0, 50, 0);

        Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                world, feet, Direction.EAST, world.standablePredicate());

        assertTrue(opening.isEmpty(),
                "20 blocks below the surface with no external air volume is not a discovery");
    }

    // ---- 4. SELF CORRIDOR ONLY (the conceptual case) ----

    @Test
    void mustNotHappen_theMobsOwnStaircaseCountsAsADiscovery() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;

        // Everything the staircase itself created or is about to create is standable.
        World world = new World();
        world.open(ControlledDescentCaveHandoff.selfCorridor(feet, heading)
                .toArray(new BlockPos[0]));

        // Context genuinely is cave-like: 20 below local terrain, no sky.
        assertEquals(CaveContextPolicy.SpaceKind.CAVE,
                ControlledDescentCaveHandoff.classifyAt(world, feet),
                "being deep in a covered staircase really is cave-like context");

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, feet, heading, world.standablePredicate()).isEmpty(),
                "cave context without a cave opportunity must produce no opening");
    }

    // ---- 2. LATERAL BREAKTHROUGH ----

    @Test
    void mustHappen_aNaturalSpaceBesideTheStairIsAnOpening() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        // Adjacent to the probe centre (feet + heading*AHEAD_PROBE = (2,50,0)). The 180-probe
        // budget against 13 vertical probes per column is spent partway through radius 2, so a
        // breakthrough further out than one ring is invisible - see the budget note below.
        BlockPos caveFloor = new BlockPos(2, 50, 1);

        World world = new World().open(caveFloor);

        Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                world, feet, heading, world.standablePredicate());

        assertTrue(opening.isPresent(), "a standable natural floor outside the corridor is evidence");
        assertEquals(caveFloor, opening.get().landing(), "the payload must name where to go");
        assertTrue(opening.get().isSubterranean());
        assertEquals(CaveContextPolicy.SpaceKind.CAVE, opening.get().kind());
        assertEquals(Direction.EAST, opening.get().continuation(),
                "continuation points at the discovery, not merely along the staircase axis");
    }

    // ---- 3. CAVE DIRECTLY AHEAD ----

    @Test
    void mustHappen_aCaveAheadIsFoundAndIsNotTheCorridorItself() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        BlockPos ahead = new BlockPos(2, 49, 0);   // probe centre, one below

        World world = new World().open(ahead);
        world.open(ControlledDescentCaveHandoff.selfCorridor(feet, heading)
                .toArray(new BlockPos[0]));

        Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                world, feet, heading, world.standablePredicate());

        assertTrue(opening.isPresent());
        assertEquals(ahead, opening.get().landing(),
                "the corridor cells are excluded, so the natural floor wins");
        assertFalse(ControlledDescentCaveHandoff.selfCorridor(feet, heading).contains(ahead));
    }

    // ---- corridor geometry ----

    @Test
    void mustHappen_selfCorridorCoversExcavatedCellsNotJustStandPositions() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Set<BlockPos> corridor = ControlledDescentCaveHandoff.selfCorridor(feet, Direction.EAST);

        assertTrue(corridor.contains(feet), "current stand");
        assertTrue(corridor.contains(feet.above()), "head space the mob occupies");
        StairStepPlan planned = StairStepPlanner.planStep(feet, Direction.EAST);
        for (BlockPos required : planned.requiredBreaks()) {
            assertTrue(corridor.contains(required),
                    "cells this step is about to break: " + required);
        }
        assertTrue(corridor.contains(planned.nextStandCell()), "next stand");
    }

    /**
     * Records a real constraint found while writing these scenarios: with
     * {@code MAX_PROBES = 180} and {@code Y_RADIUS = 6} (13 vertical probes per column), the search
     * is exhausted partway through radius 2 of {@code XZ_RADIUS = 4}. A cave whose nearest standable
     * floor sits beyond about one ring from the probe centre cannot be detected at all — the outer
     * radius is nominal. Worth knowing before anyone tunes XZ_RADIUS upward expecting more reach.
     */
    @Test
    void probeBudgetLimitsEffectiveReachBelowTheNominalRadius() {
        BlockPos feet = new BlockPos(0, 50, 0);
        BlockPos farFloor = new BlockPos(3, 50, 3);   // radius 3 from the probe centre
        World world = new World().open(farFloor);

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, feet, Direction.EAST, world.standablePredicate()).isEmpty(),
                "documents reach, not desired behaviour: this floor is real but unreachable "
                        + "within the probe budget");
    }

    @Test
    void mustNotHappen_theBooleanFacadeDisagreesWithTheEvidence() {
        BlockPos feet = new BlockPos(0, 50, 0);
        World solid = new World();
        assertFalse(ControlledDescentCaveHandoff.openedTraversableCave(
                        solid, feet, Direction.EAST, solid.standablePredicate()),
                "the retained boolean must now delegate to findOpenedCave, not to context");
    }
}
