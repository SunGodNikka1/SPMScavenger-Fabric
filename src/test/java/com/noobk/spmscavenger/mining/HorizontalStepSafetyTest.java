package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * D-MIW-TS3 / TS3-M1 — a corridor must stop for hazards, and only for hazards.
 *
 * <p>The repaired defect: the overhead check called
 * {@link StairStepSafety#validateBreakHazards}, which inspects {@code pos.above()} for falling
 * blocks. That is right for a cell being <em>excavated</em> and wrong for a cell being <em>left in
 * place</em> — so gravel resting on a solid stone ceiling, which cannot fall into the corridor,
 * rejected the step. Geometry A would have halted at random points under perfectly safe rock.
 */
class HorizontalStepSafetyTest {

    private static final BlockPos STAND = new BlockPos(0, 12, 0);
    private static final Direction HEADING = Direction.EAST;
    private static final BlockPos DESTINATION = new BlockPos(1, 12, 0);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Solid deepslate everywhere except cells explicitly set. */
    private static final class Rock implements BlockGetter {
        private final Map<BlockPos, BlockState> overrides = new HashMap<>();

        Rock set(BlockPos pos, BlockState state) {
            overrides.put(pos.immutable(), state);
            return this;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return overrides.getOrDefault(pos.immutable(), Blocks.DEEPSLATE.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private static StairStepSafety.Rejection validate(Rock world) {
        return HorizontalStepSafety.validatePlan(
                world,
                HorizontalStepPlanner.planStep(STAND, HEADING),
                StairStepSafety.BreakCapability.always());
    }

    @Test
    void mustHappen_anOrdinaryStoneCeilingPasses() {
        assertSame(StairStepSafety.Rejection.NONE, validate(new Rock()),
                "plain rock in every direction is exactly what a corridor is for");
    }

    /** The repaired case: gravel two above the head, resting on solid stone. */
    @Test
    void mustNotHappen_gravelAboveASolidCeilingStopsTheCorridor() {
        Rock world = new Rock()
                .set(DESTINATION.above(2), Blocks.DEEPSLATE.defaultBlockState())  // ceiling
                .set(DESTINATION.above(3), Blocks.GRAVEL.defaultBlockState());    // irrelevant

        assertSame(StairStepSafety.Rejection.NONE, validate(world),
                "gravel sitting on solid stone cannot fall into the tunnel - rejecting here would "
                        + "halt Geometry A under perfectly safe rock");
    }

    @Test
    void mustHappen_gravelDirectlyOverheadIsRejected() {
        Rock world = new Rock().set(DESTINATION.above(2), Blocks.GRAVEL.defaultBlockState());

        assertSame(StairStepSafety.Rejection.FALLING_BLOCK, validate(world),
                "once the head cell is cut, this drops into the corridor");
    }

    @Test
    void mustHappen_fluidOverheadIsRejected() {
        for (BlockState fluid : new BlockState[] {
                Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState()}) {
            Rock world = new Rock().set(DESTINATION.above(2), fluid);
            assertSame(StairStepSafety.Rejection.LIQUID, validate(world),
                    fluid + " directly over the opened headspace pours in");
        }
    }

    // ---- the excavated cells keep the shared primitive's semantics ----

    @Test
    void mustHappen_hazardsInTheCellsBeingCutAreStillCaught() {
        assertSame(StairStepSafety.Rejection.LIQUID,
                validate(new Rock().set(DESTINATION, Blocks.LAVA.defaultBlockState())),
                "lava in the body cell");
        assertSame(StairStepSafety.Rejection.FALLING_BLOCK,
                validate(new Rock().set(DESTINATION.above(2), Blocks.SAND.defaultBlockState())
                        .set(DESTINATION.above(1), Blocks.DEEPSLATE.defaultBlockState())),
                "sand above the head cell being cut is a genuine falling hazard");
        assertSame(StairStepSafety.Rejection.UNBREAKABLE,
                validate(new Rock().set(DESTINATION, Blocks.BEDROCK.defaultBlockState())),
                "bedrock ends the corridor rather than being mined forever");
    }

    @Test
    void mustHappen_missingFootingIsRejected() {
        Rock world = new Rock().set(DESTINATION.below(), Blocks.AIR.defaultBlockState());

        assertSame(StairStepSafety.Rejection.NO_FOOTING, validate(world),
                "a corridor stays level; stepping into a hole is not a horizontal step");
    }

    @Test
    void mustHappen_capabilityIsHonoured() {
        StairStepSafety.Rejection rejection = HorizontalStepSafety.validatePlan(
                new Rock(),
                HorizontalStepPlanner.planStep(STAND, HEADING),
                state -> state.isAir());

        assertSame(StairStepSafety.Rejection.NO_HARVEST, rejection,
                "no usable tool is a reason to stop, not to swing forever");
    }

    @Test
    void mustHappen_theStepStaysLevelAndTwoHigh() {
        StairStepPlan plan = HorizontalStepPlanner.planStep(STAND, HEADING);

        assertEquals(STAND.getY(), plan.nextStandCell().getY());
        assertEquals(2, plan.requiredBreaks().size());
    }
}
