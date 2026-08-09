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
 * MI-14-R2 — a discovery must be a <b>place</b> the mob broke into and can walk to.
 *
 * <p>Every scenario uses the production pairing: the evidence is the step just completed, and the
 * exclusion set is excavation history at the stand cell that step produced. Earlier revisions of
 * this file paired a completed step with the corridor of the <em>previous</em> stand, which is not
 * what the executor does, and that mismatch hid two defects.
 */
class CaveOpeningEvidenceTest {

    private static final int SURFACE = 70;
    private static final BlockPos S0 = new BlockPos(0, 50, 0);
    private static final Direction HEADING = Direction.EAST;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Flat hill at {@link #SURFACE}; only explicitly-added cells are anything but stone. */
    private static final class World implements ControlledDescentCaveHandoff.HeightAccess {
        private final Set<BlockPos> standable = new HashSet<>();
        private final Set<BlockPos> passable = new HashSet<>();

        /** Floor a mob can stand on. */
        World open(BlockPos... cells) {
            for (BlockPos cell : cells) {
                standable.add(cell.immutable());
                passable.add(cell.immutable());
            }
            return this;
        }

        /** Air a mob can move through but not stand in — no floor. */
        World air(BlockPos... cells) {
            for (BlockPos cell : cells) {
                passable.add(cell.immutable());
            }
            return this;
        }

        /** A cave cell with real headroom, so a two-block-tall mob fits. */
        World chamber(BlockPos floor) {
            return open(floor).air(floor.above());
        }

        /** Every cell a stair step physically excavated: a 3-high column at each stand. */
        World dug(StairStepPlan step) {
            BlockPos from = step.standCell();
            BlockPos to = step.nextStandCell();
            return open(from, from.above(), from.above(2), to, to.above(), to.above(2));
        }

        Predicate<BlockPos> passablePredicate() {
            return pos -> passable.contains(pos.immutable());
        }

        Predicate<BlockPos> standablePredicate() {
            return pos -> standable.contains(pos.immutable());
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

    /** Exactly how {@code ControlledDescentGoal.completeStep} calls the detector. */
    private static Optional<CaveOpening> detect(World world, StairStepPlan completed) {
        return ControlledDescentCaveHandoff.findOpenedCave(
                world, completed,
                ControlledDescentCaveHandoff.selfCorridor(completed.nextStandCell(), HEADING),
                world.passablePredicate(), world.standablePredicate());
    }

    private static StairStepPlan completedStep() {
        return StairStepPlanner.planStep(S0, HEADING);
    }

    @Test
    void mustNotHappen_solidHillReportsACave() {
        assertTrue(detect(new World(), completedStep()).isEmpty(),
                "20 blocks below the surface with no external air volume is not a discovery");
    }

    /** The conceptual case: cave-like <em>context</em> is not a cave <em>opportunity</em>. */
    @Test
    void mustNotHappen_theMobsOwnStaircaseCountsAsADiscovery() {
        StairStepPlan completed = completedStep();
        World world = new World().dug(completed);

        assertEquals(CaveContextPolicy.SpaceKind.CAVE,
                ControlledDescentCaveHandoff.classifyAt(world, S0),
                "being deep in a covered staircase really is cave-like context");
        assertTrue(detect(world, completed).isEmpty(),
                "cave context without a cave opportunity must produce no opening");
    }

    @Test
    void mustHappen_aNaturalSpaceBesideTheStairIsAnOpening() {
        StairStepPlan completed = completedStep();
        BlockPos lateral = new BlockPos(1, 49, 1);   // face-adjacent to the cell just cut
        World world = new World().dug(completed).chamber(lateral);

        Optional<CaveOpening> opening = detect(world, completed);
        assertTrue(opening.isPresent(), "air touching the newly cut wall is a breakthrough");
        assertEquals(lateral, opening.get().landing(), "the payload must name where to go");
        assertTrue(opening.get().isSubterranean());
        assertEquals(Direction.SOUTH, opening.get().continuation(),
                "measured from the breakthrough, not along the staircase axis");
    }

    @Test
    void mustHappen_aCaveAheadIsFoundAndIsNotTheCorridorItself() {
        StairStepPlan completed = completedStep();
        BlockPos ahead = new BlockPos(2, 49, 0);
        World world = new World().dug(completed).chamber(ahead);

        Optional<CaveOpening> opening = detect(world, completed);
        assertTrue(opening.isPresent());
        assertEquals(ahead, opening.get().landing(),
                "the corridor cells are excluded, so the natural floor wins");
        assertFalse(ControlledDescentCaveHandoff
                .selfCorridor(completed.nextStandCell(), HEADING).contains(ahead));
    }

    /** MI-14-R2c/R2d — the corridor is excavation history, at the full height a step cuts. */
    @Test
    void mustHappen_selfCorridorIsDugHistoryAtFullStepHeight() {
        Set<BlockPos> corridor = ControlledDescentCaveHandoff.selfCorridor(S0, HEADING);

        assertTrue(corridor.contains(S0), "current stand");
        assertTrue(corridor.contains(S0.above()), "head space the mob occupies");
        assertTrue(corridor.contains(S0.above(2)),
                "R2c: the step that produced this stand cut headroom here too - omitting it let "
                        + "the flood report the staircase's own ceiling as a cave");

        StairStepPlan planned = StairStepPlanner.planStep(S0, HEADING);
        for (BlockPos required : planned.requiredBreaks()) {
            assertFalse(corridor.contains(required),
                    "R2d: not yet dug, so not self-created: " + required);
        }
        assertFalse(corridor.contains(planned.nextStandCell()), "R2d: next stand is not dug yet");
    }

    @Test
    void mustNotHappen_aCaveBehindUnbrokenStoneCountsAsOpened() {
        StairStepPlan completed = completedStep();
        // (2,49,0) stays solid as the separating wall; a perfect cave floor sits behind it.
        World world = new World().dug(completed).chamber(new BlockPos(3, 49, 0));

        assertTrue(detect(world, completed).isEmpty(),
                "geographically close is not topologically connected - the wall is still there");
    }

    @Test
    void mustHappen_theSameCaveIsFoundOnceTheWallIsGone() {
        StairStepPlan completed = completedStep();
        BlockPos hidden = new BlockPos(3, 49, 0);
        World world = new World().dug(completed).chamber(hidden)
                .air(new BlockPos(2, 49, 0), new BlockPos(2, 50, 0));   // wall now excavated

        Optional<CaveOpening> opening = detect(world, completed);
        assertTrue(opening.isPresent(), "connected air now reaches the cave floor");
        assertEquals(hidden, opening.get().landing(),
                "the identical cell that was correctly rejected a moment ago");
    }

    @Test
    void mustNotHappen_theBooleanFacadeDisagreesWithTheEvidence() {
        World solid = new World();
        assertFalse(ControlledDescentCaveHandoff.openedTraversableCave(
                        solid, S0, HEADING, solid.standablePredicate()),
                "the retained boolean must now delegate to findOpenedCave, not to context");
    }

    // ---- MI-14-R2c: WHICH STEP IS THE EVIDENCE ----

    /** A breakthrough on the completed step is visible; from the future step it is not. */
    @Test
    void mustHappen_theJustCompletedStepIsWhatCountsAsEvidence() {
        StairStepPlan completed = completedStep();
        World world = new World().dug(completed).chamber(new BlockPos(1, 49, 1));

        assertTrue(detect(world, completed).isPresent(),
                "the step that was just dug is the evidence source");

        StairStepPlan future = StairStepPlanner.planStep(completed.nextStandCell(), HEADING);
        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, future,
                        ControlledDescentCaveHandoff.selfCorridor(future.nextStandCell(), HEADING),
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "planning from current feet describes S1->S2 and misses the real opening");
    }

    /** A cave touching the still-solid wall ahead is not an opening. */
    @Test
    void mustNotHappen_aCaveTouchingTheUnbrokenWallAheadIsReported() {
        StairStepPlan completed = completedStep();
        StairStepPlan future = StairStepPlanner.planStep(completed.nextStandCell(), HEADING);
        World world = new World().dug(completed).chamber(future.nextStandCell().south());

        for (BlockPos required : future.requiredBreaks()) {
            assertFalse(world.passablePredicate().test(required),
                    "precondition: the next step is still solid " + required);
        }
        assertTrue(detect(world, completed).isEmpty(),
                "nothing has been dug through to it - the wall ahead is intact");
    }

    /** The seed guard, independent of wiring: an unexcavated step opens nothing. */
    @Test
    void mustNotHappen_anUndugStepSeedsABreakthrough() {
        StairStepPlan completed = completedStep();
        StairStepPlan future = StairStepPlanner.planStep(completed.nextStandCell(), HEADING);
        World world = new World().dug(completed).chamber(future.nextStandCell().south());

        assertTrue(ControlledDescentCaveHandoff.findOpenedCave(
                        world, future,
                        ControlledDescentCaveHandoff.selfCorridor(future.nextStandCell(), HEADING),
                        world.passablePredicate(), world.standablePredicate()).isEmpty(),
                "solid cells are not seeds regardless of which plan was handed in");
    }
}
