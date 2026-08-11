package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GAO-6R — companion {@link ExperienceKind#SOCIAL_EXPEDITION} must not close the parent expedition
 * episode.
 */
class SocialCompanionEpisodeRepairTest {

    private static final UUID LEADER = UUID.randomUUID();
    private static final UUID COMPANION_A = UUID.randomUUID();
    private static final UUID COMPANION_B = UUID.randomUUID();
    private static final long WORLD_AGE = 2_000_000L;
    private static final long SESSION_DURATION = 600L;
    private static final BlockPos AT = new BlockPos(0, 64, 0);

    @BeforeEach
    void enableOpinion() {
        OpinionFeatureGate.setTestOverride(true);
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void companionInviteDoesNotCloseExplorationEpisode() {
        UUID expeditionId = expeditionId(WORLD_AGE);
        unlockExpedition(expeditionId, WORLD_AGE);
        socialCompanionJoined(expeditionId, COMPANION_A, WORLD_AGE + 1L);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        ActivityEpisode expedition = context.findEpisode(expeditionId).orElseThrow();

        assertFalse(expedition.isClosed(), "SOCIAL_EXPEDITION must not close OVERLAND exploration");
        assertFalse(context.hasCompletedEpisode(expeditionId));
    }

    @Test
    void expeditionEndStillCommitsOverlandLearningAfterCompanionJoin() {
        UUID expeditionId = expeditionId(WORLD_AGE);
        long closeTick = WORLD_AGE + SESSION_DURATION;
        unlockExpedition(expeditionId, WORLD_AGE);
        socialCompanionJoined(expeditionId, COMPANION_A, WORLD_AGE + 1L);
        endExpedition(expeditionId, WORLD_AGE, closeTick);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        assertTrue(context.hasCompletedEpisode(expeditionId));
        assertTrue(context.opinionMemory().preference(ActivityKind.OVERLAND_EXPLORATION) > 0f);
        assertEquals(
                SESSION_DURATION,
                context.opinionMemory().memoryOf(ActivityKind.OVERLAND_EXPLORATION).recentDuration());
    }

    @Test
    void eachCompanionGetsOwnSocialSubEpisodeAndLearning() {
        UUID expeditionId = expeditionId(WORLD_AGE);
        unlockExpedition(expeditionId, WORLD_AGE);
        socialCompanionJoined(expeditionId, COMPANION_A, WORLD_AGE + 1L);
        socialCompanionJoined(expeditionId, COMPANION_B, WORLD_AGE + 2L);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        UUID socialA = SocialExperienceEpisodes.companionInviteEpisodeId(expeditionId, COMPANION_A);
        UUID socialB = SocialExperienceEpisodes.companionInviteEpisodeId(expeditionId, COMPANION_B);

        assertNotEquals(socialA, socialB);
        assertTrue(context.hasCompletedEpisode(socialA));
        assertTrue(context.hasCompletedEpisode(socialB));
        assertFalse(context.findEpisode(expeditionId).orElseThrow().isClosed());
        assertEquals(8f, context.entityOpinionMemory().preference(COMPANION_A), 0.001f);
        assertEquals(8f, context.entityOpinionMemory().preference(COMPANION_B), 0.001f);
        assertTrue(context.opinionMemory().preference(ActivityKind.SOCIALIZING) > 0f);
        assertTrue(context.affectiveState().engagement() > 0f);
    }

    @Test
    void duplicateCompanionInviteIsIdempotent() {
        UUID expeditionId = expeditionId(WORLD_AGE);
        unlockExpedition(expeditionId, WORLD_AGE);
        socialCompanionJoined(expeditionId, COMPANION_A, WORLD_AGE + 1L);
        float socializingAfterFirst =
                OpinionExperienceRegistry.contextFor(LEADER).opinionMemory().preference(ActivityKind.SOCIALIZING);
        float engagementAfterFirst =
                OpinionExperienceRegistry.contextFor(LEADER).affectiveState().engagement();

        socialCompanionJoined(expeditionId, COMPANION_A, WORLD_AGE + 2L);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        assertEquals(8f, context.entityOpinionMemory().preference(COMPANION_A), 0.001f);
        assertEquals(socializingAfterFirst, context.opinionMemory().preference(ActivityKind.SOCIALIZING), 0.001f);
        assertEquals(engagementAfterFirst, context.affectiveState().engagement(), 0.001f);
    }

    @Test
    void socialEpisodeIdIsDeterministicPerExpeditionAndCompanion() {
        UUID expeditionId = expeditionId(WORLD_AGE);
        UUID first = SocialExperienceEpisodes.companionInviteEpisodeId(expeditionId, COMPANION_A);
        UUID second = SocialExperienceEpisodes.companionInviteEpisodeId(expeditionId, COMPANION_A);
        UUID otherCompanion =
                SocialExperienceEpisodes.companionInviteEpisodeId(expeditionId, COMPANION_B);

        assertEquals(first, second);
        assertNotEquals(first, otherCompanion);
        assertNotEquals(first, expeditionId);
    }

    private static UUID expeditionId(long startedTick) {
        return RestSessionCoordinator.episodeIdForProject(LEADER, startedTick, "EXPEDITION");
    }

    private static void unlockExpedition(UUID episodeId, long tick) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        context.ensureEpisode(episodeId, tick, java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION));
        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_UNLOCKED,
                tick,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_UNLOCKED,
                0.25f,
                -0.1f,
                0.0f,
                0.0f,
                0.5f,
                java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                java.util.Optional.empty(),
                java.util.Optional.empty()));
    }

    private static void socialCompanionJoined(UUID expeditionId, UUID companionId, long tick) {
        ExperienceEmitters.socialCompanionJoined(LEADER, companionId, expeditionId, AT, tick);
    }

    private static void endExpedition(UUID episodeId, long started, long closeTick) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(LEADER);
        context.ensureEpisode(episodeId, started, java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION));
        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_END,
                closeTick,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_COMPLETE,
                0.0f,
                0.0f,
                0.2f,
                0.0f,
                0.0f,
                java.util.Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                java.util.Optional.empty(),
                java.util.Optional.empty()));
    }
}
