package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MiningTerminalSemantics;
import com.noobk.spmscavenger.mining.MiningExecutionLease;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import com.noobk.spmscavenger.mining.MiningProjectEnd;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceOpinionServiceTest {

    private static final UUID MOB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OpinionFeatureGate.testOverride = true;
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.testOverride = null;
    }

    /** D-GAO-024: place learning now requires evidence that the executor actually ran. */
    private static MiningExecutionLease executed() {
        return MiningExecutionLease.issued(MiningProjectMode.CONTROLLED_DESCENT, 0L).started(1L);
    }

    @Test
    void caveFoundIncreasesPlacePreference() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(16, 64, 32);

        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, executed()),
                ActivityKind.CAVE_EXPLORATION,
                at);

        assertEquals(18f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void noProgressDecreasesPlacePreferenceOnlyAfterRepetition() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(0, 64, 0);

        // D-GAO-024: place learning now honours the shared EXECUTION_FAILURE threshold instead of
        // bypassing it with its own table. One stall is bad luck; the policy wants repetition.
        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.NO_PROGRESS, executed()),
                ActivityKind.CAVE_EXPLORATION,
                at);
        assertEquals(0f, context.placeOpinionMemory().preference(new ChunkPos(at)),
                "a single execution failure is below EXECUTION_FAILURE_LEARNING_THRESHOLD");

        context.registerExecutionFailure(ActivityKind.CAVE_EXPLORATION);
        context.registerExecutionFailure(ActivityKind.CAVE_EXPLORATION);
        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.NO_PROGRESS, executed()),
                ActivityKind.CAVE_EXPLORATION,
                at);

        assertEquals(-14f, context.placeOpinionMemory().preference(new ChunkPos(at)),
                "repeated physical no-progress while actually mining is legitimate evidence");
    }

    @Test
    void opinionDisabledSkipsLearning() {
        OpinionFeatureGate.testOverride = false;
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(4, 64, 4);

        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, executed()),
                ActivityKind.CAVE_EXPLORATION,
                at);

        assertEquals(0f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void frozenContextSkipsLearning() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.freeze();
        BlockPos at = new BlockPos(4, 64, 4);

        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, executed()),
                ActivityKind.CAVE_EXPLORATION,
                at);

        assertEquals(0f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void deathClearsPlaceMemory() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        PlaceOpinionService.applyMiningTerminal(
                context,
                MiningTerminalSemantics.of(MiningProjectEnd.CAVE_FOUND, executed()),
                ActivityKind.CAVE_EXPLORATION,
                new BlockPos(1, 64, 1));
        assertTrue(context.placeOpinionMemory().trackedPlaceCount() > 0);

        OpinionExperienceRegistry.onDeath(MOB);

        assertEquals(0, context.placeOpinionMemory().trackedPlaceCount());
    }
}
