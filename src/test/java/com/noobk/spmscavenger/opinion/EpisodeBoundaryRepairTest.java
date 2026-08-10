package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityEpisode;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceEmitters;
import com.noobk.spmscavenger.experience.ExperienceEvent;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.ExpeditionEndAttribution;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.OutcomeClass;
import com.noobk.spmscavenger.experience.RestAnchorType;
import com.noobk.spmscavenger.experience.RestCloseReason;
import com.noobk.spmscavenger.experience.RestSessionClaim;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.experience.RestSourceKind;
import com.noobk.spmscavenger.mining.MiningProjectMode;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodeBoundaryRepairTest {

    private static final UUID MOB = UUID.randomUUID();
    private static final long WORLD_AGE = 1_000_000L;
    private static final long SESSION_DURATION = 600L;

    @BeforeEach
    void enableOpinion() {
        OpinionFeatureGate.testOverride = true;
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.testOverride = null;
    }

    @Test
    void restOpenLeavesEpisodeOpenWithoutTerminalLearning() {
        RestSessionClaim claim = restClaim(WORLD_AGE);
        ExperienceEmitters.restSessionOpened(MOB, claim, WORLD_AGE);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        ActivityEpisode episode = context.findEpisode(claim.claimId()).orElseThrow();

        assertFalse(episode.isClosed());
        assertEquals(WORLD_AGE, episode.openedAtGameTime());
        assertEquals(0.0f, context.opinionMemory().preference(ActivityKind.REST));
    }

    @Test
    void restOpenAloneDoesNotSatisfyTimeoutSuccessLearning() {
        RestSessionClaim claim = restClaim(WORLD_AGE);
        ExperienceEmitters.restSessionOpened(MOB, claim, WORLD_AGE);

        assertEquals(0.0f, OpinionExperienceRegistry.contextFor(MOB).opinionMemory().preference(ActivityKind.REST));
    }

    @Test
    void restTimeoutCloseCommitsLearningOnceWithRealDuration() {
        RestSessionClaim claim = restClaim(WORLD_AGE);
        long closeTick = WORLD_AGE + SESSION_DURATION;
        ExperienceEmitters.restSessionOpened(MOB, claim, WORLD_AGE);
        ExperienceEmitters.restSessionClosed(
                MOB, claim.closed(RestCloseReason.TIMEOUT, closeTick), RestCloseReason.TIMEOUT, closeTick);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        ActivityEpisode episode = context.findEpisode(claim.claimId()).orElseThrow();

        assertTrue(episode.isClosed());
        assertTrue(context.opinionMemory().preference(ActivityKind.REST) > 0.0f);
        assertEquals(SESSION_DURATION, context.opinionMemory().memoryOf(ActivityKind.REST).recentDuration());
    }

    @Test
    void restCombatCloseReachesEpisodeWithoutDislikeLearning() {
        RestSessionClaim claim = restClaim(WORLD_AGE);
        long closeTick = WORLD_AGE + 50L;
        ExperienceEmitters.restSessionOpened(MOB, claim, WORLD_AGE);
        float beforeClose = OpinionExperienceRegistry.contextFor(MOB).opinionMemory().preference(ActivityKind.REST);

        ExperienceEmitters.restSessionClosed(
                MOB, claim.closed(RestCloseReason.COMBAT, closeTick), RestCloseReason.COMBAT, closeTick);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        assertTrue(context.findEpisode(claim.claimId()).orElseThrow().isClosed());
        assertEquals(beforeClose, context.opinionMemory().preference(ActivityKind.REST), 0.0001f);
    }

    @Test
    void fireInvalidCloseIsConsumedAfterOpen() {
        RestSessionClaim claim = restClaim(WORLD_AGE);
        ExperienceEmitters.restSessionOpened(MOB, claim, WORLD_AGE);
        long closeTick = WORLD_AGE + 40L;
        ExperienceEmitters.restSessionClosed(
                MOB, claim.closed(RestCloseReason.FIRE_INVALID, closeTick),
                RestCloseReason.FIRE_INVALID, closeTick);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        assertTrue(context.findEpisode(claim.claimId()).orElseThrow().isClosed());
        assertEquals(0.0f, context.opinionMemory().preference(ActivityKind.REST));
    }

    @Test
    void expeditionUnlockDoesNotCloseEpisodeOrLearnPreference() {
        UUID episodeId = UUID.randomUUID();
        unlockExpedition(episodeId, WORLD_AGE);

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        ActivityEpisode episode = context.findEpisode(episodeId).orElseThrow();
        assertFalse(episode.isClosed());
        assertEquals(WORLD_AGE, episode.openedAtGameTime());
        assertEquals(0.0f, context.opinionMemory().preference(ActivityKind.OVERLAND_EXPLORATION));
    }

    @Test
    void expeditionCompletionClosesEpisodeAsSuccess() {
        UUID episodeId = UUID.randomUUID();
        unlockExpedition(episodeId, WORLD_AGE);
        long closeTick = WORLD_AGE + SESSION_DURATION;
        endExpedition(episodeId, WORLD_AGE, closeTick, ExpeditionEndAttribution.completed());

        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        assertTrue(context.findEpisode(episodeId).orElseThrow().isClosed());
        assertTrue(context.opinionMemory().preference(ActivityKind.OVERLAND_EXPLORATION) > 0.0f);
        assertEquals(SESSION_DURATION, context.opinionMemory()
                .memoryOf(ActivityKind.OVERLAND_EXPLORATION).recentDuration());
    }

    @Test
    void expeditionSimulationFrontierClosesWithoutDislikeLearning() {
        UUID episodeId = UUID.randomUUID();
        unlockExpedition(episodeId, WORLD_AGE);
        endExpedition(
                episodeId,
                WORLD_AGE,
                WORLD_AGE + 100L,
                ExpeditionEndAttribution.simulationFrontier());

        assertEquals(
                0.0f,
                OpinionExperienceRegistry.contextFor(MOB).opinionMemory()
                        .preference(ActivityKind.OVERLAND_EXPLORATION));
    }

    @Test
    void miningEpisodePreservesProjectStartTimeForDuration() {
        UUID episodeId = RestSessionCoordinator.episodeIdForProject(
                MOB, WORLD_AGE, MiningProjectMode.TUNNEL_SEARCH.name());
        long closeTick = WORLD_AGE + SESSION_DURATION;
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.ensureEpisode(episodeId, WORLD_AGE, Optional.of(ActivityKind.TUNNEL_SEARCH));
        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.PROJECT_END,
                closeTick,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.UNSPECIFIED,
                0.0f,
                0.0f,
                0.2f,
                0.0f,
                0.0f,
                Optional.of(ActivityKind.TUNNEL_SEARCH),
                Optional.empty(),
                Optional.empty()));

        assertEquals(
                SESSION_DURATION,
                context.opinionMemory().memoryOf(ActivityKind.TUNNEL_SEARCH).recentDuration());
    }

    private static RestSessionClaim restClaim(long arrivedAt) {
        UUID claimId = UUID.randomUUID();
        return new RestSessionClaim(
                claimId,
                Optional.empty(),
                claimId,
                RestSourceKind.DISCRETIONARY_REST,
                new BlockPos(1, 64, 2),
                RestAnchorType.CAMPFIRE,
                arrivedAt,
                arrivedAt,
                arrivedAt,
                Optional.empty());
    }

    private static void unlockExpedition(UUID episodeId, long tick) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.ensureEpisode(episodeId, tick, Optional.of(ActivityKind.OVERLAND_EXPLORATION));
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
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.empty(),
                Optional.empty()));
    }

    private static void endExpedition(
            UUID episodeId,
            long started,
            long closeTick,
            ExpeditionEndAttribution.Semantics semantics) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        context.ensureEpisode(episodeId, started, Optional.of(ActivityKind.OVERLAND_EXPLORATION));
        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.EXPEDITION_END,
                closeTick,
                episodeId,
                semantics.outcome(),
                semantics.cause(),
                0.0f,
                0.0f,
                semantics.satisfactionDelta(),
                0.0f,
                0.0f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.empty(),
                Optional.empty()));
    }
}
