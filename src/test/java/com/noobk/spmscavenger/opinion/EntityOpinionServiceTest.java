package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityOpinionServiceTest {

    private static final UUID MOB = UUID.randomUUID();
    private static final UUID COMPANION = UUID.randomUUID();

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
    void companionInviteRecordsLearnedAffinity() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);

        EntityOpinionService.applyCompanionInvite(context, COMPANION);

        assertEquals(8f, context.entityOpinionMemory().preference(COMPANION), 0.001f);
    }

    @Test
    void socialInteractionRecordsCustomDelta() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);

        EntityOpinionService.applySocialInteraction(context, COMPANION, 5f);

        assertEquals(5f, context.entityOpinionMemory().preference(COMPANION), 0.001f);
    }

    @Test
    void opinionDisabledSkipsLearning() {
        OpinionFeatureGate.testOverride = false;
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);

        EntityOpinionService.applyCompanionInvite(context, COMPANION);

        assertEquals(0f, context.entityOpinionMemory().preference(COMPANION), 0.001f);
    }

    @Test
    void frozenContextSkipsLearning() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.freeze();

        EntityOpinionService.applyCompanionInvite(context, COMPANION);

        assertEquals(0f, context.entityOpinionMemory().preference(COMPANION), 0.001f);
    }

    @Test
    void deathClearsEntityMemory() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        EntityOpinionService.applyCompanionInvite(context, COMPANION);
        assertTrue(context.entityOpinionMemory().trackedEntityCount() > 0);

        OpinionExperienceRegistry.onDeath(MOB);

        assertEquals(0, context.entityOpinionMemory().trackedEntityCount());
    }
}
