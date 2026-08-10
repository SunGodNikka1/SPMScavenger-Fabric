package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RT-GAO minimal sanity — static falsification of GAO-PARITY, GAO-4.1 threshold wiring, and GAO-5
 * place-memory hooks without Minecraft launch.
 */
class RtGaoMinimalSanityTest {

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
    void gaoParityOpinionOffPreservesExploreIdleThreshold() {
        OpinionFeatureGate.testOverride = false;
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.exploreIdleTicks = 600;

        assertEquals(600, ExploreReadinessThresholds.idleTicks(cfg, MOB));
    }

    @Test
    void gao41HighBoredomLowersSharedIdleThreshold() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.exploreIdleTicks = 600;
        OpinionExperienceRegistry.contextFor(MOB).affectiveState().seedChannels(0f, 100f, 0f, 0f, 0f);

        int bored = ExploreReadinessThresholds.idleTicks(cfg, MOB);
        OpinionExperienceRegistry.contextFor(MOB).affectiveState().seedChannels(0f, 0f, 0f, 0f, 0f);
        int neutral = ExploreReadinessThresholds.idleTicks(cfg, MOB);

        assertEquals(375, bored);
        assertEquals(600, neutral);
        assertTrue(bored < neutral);
    }

    @Test
    void gao5PlaceMemoryLivesOnExperienceContext() {
        var context = OpinionExperienceRegistry.contextFor(MOB);
        context.placeOpinionMemory().recordOutcome(0L, 12f);
        assertEquals(12f, context.placeOpinionMemory().preference(0L));
    }
}
