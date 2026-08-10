package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
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

    @Test
    void caveFoundIncreasesPlacePreference() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(16, 64, 32);

        PlaceOpinionService.applyMiningTerminal(context, MiningProjectEnd.CAVE_FOUND, at);

        assertEquals(18f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void noProgressDecreasesPlacePreference() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(0, 64, 0);

        PlaceOpinionService.applyMiningTerminal(context, MiningProjectEnd.NO_PROGRESS, at);

        assertEquals(-14f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void opinionDisabledSkipsLearning() {
        OpinionFeatureGate.testOverride = false;
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        BlockPos at = new BlockPos(4, 64, 4);

        PlaceOpinionService.applyMiningTerminal(context, MiningProjectEnd.CAVE_FOUND, at);

        assertEquals(0f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void frozenContextSkipsLearning() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.freeze();
        BlockPos at = new BlockPos(4, 64, 4);

        PlaceOpinionService.applyMiningTerminal(context, MiningProjectEnd.CAVE_FOUND, at);

        assertEquals(0f, context.placeOpinionMemory().preference(new ChunkPos(at)));
    }

    @Test
    void deathClearsPlaceMemory() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        PlaceOpinionService.applyMiningTerminal(context, MiningProjectEnd.CAVE_FOUND, new BlockPos(1, 64, 1));
        assertTrue(context.placeOpinionMemory().trackedPlaceCount() > 0);

        OpinionExperienceRegistry.onDeath(MOB);

        assertEquals(0, context.placeOpinionMemory().trackedPlaceCount());
    }
}
