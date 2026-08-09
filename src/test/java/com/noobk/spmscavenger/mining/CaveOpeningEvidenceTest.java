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
        private final Set<BlockPos> passable = new HashSet<>();

        /** A standable cell is necessarily passable; everything else is solid stone. */
        World open(BlockPos... cells) {
            for (BlockPos cell : cells) {
                standable.add(cell.immutable());
                passable.add(cell.immutable());
            }
            return this;
        }

        /** Air the mob can move through but not stand in — no floor. */
        World air(BlockPos... cells) {
            for (BlockPos cell : cells) {
                passable.add(cell.immutable());
            }
            return this;
        }

        Predicate<BlockPos> passablePredicate() {
            return pos -> passable.contains(pos.immutable());
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
                world, StairStepPlanner.planStep(feet, Direction.EAST),
                ControlledDescentCaveHandoff.selfCorridor(feet, Direction.EAST),
                world.passablePredicate(), world.standablePredicate());

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
                        world, StairStepPlanner.planStep(feet, heading),
                ControlledDescentCaveHandoff.selfCorridor(feet, heading),
                world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "cave context without a cave opportunity must produce no opening");
    }

    // ---- 4. LATERAL BREAKTHROUGH ----

    @Test
    void mustHappen_aNaturalSpaceBesideTheStairIsAnOpening() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        // The step opens (1,50,0). A cave lying immediately south of that cut wall is a genuine
        // lateral breakthrough: face-adjacent to what was just excavated.
        //
        // This scenario previously placed the cave diagonally at (2,50,1), touching nothing. Under
        // the R2a volume scan that still counted; under R2b it correctly does not, which is the
        // whole point of the change.
        BlockPos lateral = new BlockPos(1, 50, 1);

        World world = new World().open(lateral);
        world.open(ControlledDescentCaveHandoff.selfCorridor(feet, heading).toArray(new BlockPos[0]));

        Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                world, StairStepPlanner.planStep(feet, heading),
                ControlledDescentCaveHandoff.selfCorridor(feet, heading),
                world.passablePredicate(), world.standablePredicate());

        assertTrue(opening.isPresent(), "air touching the newly cut wall is a breakthrough");
        assertEquals(lateral, opening.get().landing(), "the payload must name where to go");
        assertTrue(opening.get().isSubterranean());
        assertEquals(CaveContextPolicy.SpaceKind.CAVE, opening.get().kind());
        assertEquals(Direction.SOUTH, opening.get().continuation(),
                "continuation points at the discovery, not along the staircase axis");
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
                world, StairStepPlanner.planStep(feet, heading),
                ControlledDescentCaveHandoff.selfCorridor(feet, heading),
                world.passablePredicate(), world.standablePredicate());

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
        assertTrue(corridor.contains(feet.above(2)),
                "MI-14-R2c: the step that produced this stand cut headroom here too - omitting it "
                        + "let the flood report the staircase's own ceiling as a cave");
        StairStepPlan planned = StairStepPlanner.planStep(feet, Direction.EAST);
        for (BlockPos required : planned.requiredBreaks()) {
            assertTrue(corridor.contains(required),
                    "cells this step is about to break: " + required);
        }
        assertTrue(corridor.contains(planned.nextStandCell()), "next stand");
    }

    // ---- 2. HIDDEN CAVE BEHIND AN INTACT WALL (MI-14-R2b) ----

    @Test
    void mustNotHappen_aCaveBehindUnbrokenStoneCountsAsOpened() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        // The step opens (1,51,0),(1,50,0),(1,49,0). Leave (2,49,0) SOLID as the separating wall,
        // and put a perfectly good cave floor behind it at (3,49,0).
        BlockPos hidden = new BlockPos(3, 49, 0);
        World world = new World().open(hidden);
        world.open(ControlledDescentCaveHandoff.selfCorridor(feet, heading).toArray(new BlockPos[0]));

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, StairStepPlanner.planStep(feet, heading),
                        ControlledDescentCaveHandoff.selfCorridor(feet, heading),
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "geographically close is not topologically connected - the wall is still there");
    }

    // ---- 3. THE SAME CAVE ONCE THE CONNECTING BLOCK BREAKS ----

    @Test
    void mustHappen_theSameCaveIsFoundOnceTheWallIsGone() {
        BlockPos feet = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        BlockPos hidden = new BlockPos(3, 49, 0);

        World world = new World().open(hidden);
        world.open(ControlledDescentCaveHandoff.selfCorridor(feet, heading).toArray(new BlockPos[0]));
        world.air(new BlockPos(2, 49, 0));   // the separating block is now excavated

        Optional<CaveOpening> opening = ControlledDescentCaveHandoff.findOpenedCave(
                world, StairStepPlanner.planStep(feet, heading),
                ControlledDescentCaveHandoff.selfCorridor(feet, heading),
                world.passablePredicate(), world.standablePredicate());

        assertTrue(opening.isPresent(), "connected air now reaches the cave floor");
        assertEquals(hidden, opening.get().landing(),
                "the identical cell that was correctly rejected a moment ago");
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
                        world, StairStepPlanner.planStep(feet, Direction.EAST),
                ControlledDescentCaveHandoff.selfCorridor(feet, Direction.EAST),
                world.passablePredicate(), world.standablePredicate()).isEmpty(),
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

    // ---- MI-14-R2c: WHICH STEP IS THE EVIDENCE ----
    //
    // A staircase step runs S0 -> S1; the mob stands at S1 when the step completes. The evidence is
    // what S0->S1 opened. Planning from S1 describes S1->S2 - the *next* step, still solid rock.

    /** TEST A — a breakthrough on the completed step is visible; from the future step it is not. */
    @Test
    void mustHappen_theJustCompletedStepIsWhatCountsAsEvidence() {
        BlockPos s0 = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        StairStepPlan completed = StairStepPlanner.planStep(s0, heading);   // S0 -> S1
        BlockPos s1 = completed.nextStandCell();                           // (1,49,0)

        // Cave face-adjacent to a cell THIS step opened, and to nothing the next step will open.
        BlockPos breakthrough = new BlockPos(1, 49, 1);

        // Dig only what S0->S1 dug. selfCorridor(s1) also contains the *planned* next step, so
        // opening it would open the very wall these scenarios need intact.
        World world = new World().open(breakthrough);
        world.open(ControlledDescentCaveHandoff.selfCorridor(s0, heading).toArray(new BlockPos[0]));
        Set<BlockPos> corridor = ControlledDescentCaveHandoff.selfCorridor(s1, heading);

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, completed, corridor,
                        world.passablePredicate(), world.standablePredicate()).isPresent(),
                "the step that was just dug is the evidence source");

        // The defect this test exists for: asking about the step that has not happened yet is blind
        // to the breakthrough that just did.
        StairStepPlan future = StairStepPlanner.planStep(s1, heading);     // S1 -> S2
        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, future, corridor,
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "planning from current feet describes S1->S2 and misses the real opening");
    }

    /** TEST B — a cave touching the still-solid wall ahead is not an opening. */
    @Test
    void mustNotHappen_aCaveTouchingTheUnbrokenWallAheadIsReported() {
        BlockPos s0 = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        StairStepPlan completed = StairStepPlanner.planStep(s0, heading);
        BlockPos s1 = completed.nextStandCell();
        StairStepPlan future = StairStepPlanner.planStep(s1, heading);

        // Touches a cell the NEXT step will break - (2,48,0) - which is still solid stone.
        BlockPos beyondTheWall = future.nextStandCell().south();

        World world = new World().open(beyondTheWall);
        world.open(ControlledDescentCaveHandoff.selfCorridor(s0, heading).toArray(new BlockPos[0]));
        Set<BlockPos> corridor = ControlledDescentCaveHandoff.selfCorridor(s1, heading);

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, completed, corridor,
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "nothing has been dug through to it - the wall ahead is intact");
    }

    /**
     * The seed guard, independent of wiring: an unexcavated step opens nothing, so even a caller
     * that passes the wrong plan cannot manufacture a breakthrough through solid rock.
     */
    @Test
    void mustNotHappen_anUndugStepSeedsABreakthrough() {
        BlockPos s0 = new BlockPos(0, 50, 0);
        Direction heading = Direction.EAST;
        StairStepPlan completed = StairStepPlanner.planStep(s0, heading);
        StairStepPlan future = StairStepPlanner.planStep(completed.nextStandCell(), heading);
        BlockPos beyondTheWall = future.nextStandCell().south();

        World world = new World().open(beyondTheWall);
        world.open(ControlledDescentCaveHandoff.selfCorridor(s0, heading).toArray(new BlockPos[0]));

        for (BlockPos required : future.requiredBreaks()) {
            assertFalse(world.passablePredicate().test(required),
                    "precondition: the next step is still solid " + required);
        }
        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, future,
                        ControlledDescentCaveHandoff.selfCorridor(completed.nextStandCell(), heading),
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "solid cells are not seeds regardless of which plan was handed in");
    }
}
