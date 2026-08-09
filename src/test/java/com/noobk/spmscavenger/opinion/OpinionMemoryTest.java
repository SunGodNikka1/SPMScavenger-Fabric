package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityEpisode;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.ExperienceEvent;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.OutcomeClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpinionMemoryTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID EPISODE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

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
    void longMiningSessionRaisesRepetitionMoreThanPreference() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        ActivityEpisode episode = context.openEpisode(Optional.of(ActivityKind.TUNNEL_SEARCH), 0L);
        UUID episodeId = episode.episodeId();

        for (int i = 0; i < 64; i++) {
            episode.ingest(blockBroken(episodeId, i), context.sinks(), context);
        }

        ActivityOpinionMemory memory = context.opinionMemory().memoryOf(ActivityKind.TUNNEL_SEARCH);
        assertTrue(memory.repetition() > memory.preference(),
                "repetition should dominate after a long normalized mining window");
        assertTrue(memory.preference() > 0f, "successful mining history should stay positive");
    }

    @Test
    void longTerminalSessionAddsDurationRepetitionWithoutCollapsingPreference() {
        OpinionMemory memory = new OpinionMemory();
        long closedAt = OpinionMemory.LONG_SESSION_TICKS;
        float repetitionBefore = memory.repetition(ActivityKind.OVERLAND_EXPLORATION);

        memory.apply(milestone(ActivityKind.OVERLAND_EXPLORATION, 0.125f, 100L), 0L);
        memory.apply(terminal(
                ActivityKind.OVERLAND_EXPLORATION,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_UNLOCKED,
                1.0f,
                closedAt), closedAt);

        ActivityOpinionMemory explore = memory.memoryOf(ActivityKind.OVERLAND_EXPLORATION);
        assertTrue(explore.preference() > 0f);
        assertTrue(explore.repetition() > repetitionBefore);
        assertEquals(closedAt, explore.recentDuration());
    }

    @Test
    void deathPreservesPreferenceButClearsEpisodicPressure() {
        OpinionMemory memory = new OpinionMemory();
        memory.apply(terminal(
                ActivityKind.TUNNEL_SEARCH,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.UNSPECIFIED,
                1.0f,
                100L), 0L);
        memory.apply(milestone(ActivityKind.TUNNEL_SEARCH, 0.125f, 50L), 0L);
        memory.apply(terminal(
                ActivityKind.TUNNEL_SEARCH,
                OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_NO_PROGRESS,
                -0.5f,
                200L), 200L);
        ActivityOpinionMemory before = memory.memoryOf(ActivityKind.TUNNEL_SEARCH);
        float preferenceBefore = before.preference();
        assertTrue(before.repetition() > 0f);
        assertEquals(1, before.recentFailures());

        memory.onDeath();
        ActivityOpinionMemory after = memory.memoryOf(ActivityKind.TUNNEL_SEARCH);
        assertEquals(preferenceBefore, after.preference(), 0.0001f);
        assertEquals(0f, after.repetition(), 0.0001f);
        assertEquals(0L, after.recentDuration());
        assertEquals(0, after.recentFailures());
    }

    @Test
    void authorityCancelEvidenceIsRejected() {
        OpinionMemory memory = new OpinionMemory();
        EpisodeLearningEvidence blocked = new EpisodeLearningEvidence(
                EPISODE,
                Optional.of(ActivityKind.REST),
                ExperienceKind.REST_SESSION,
                OutcomeClass.AUTHORITY_CANCEL,
                ExperienceCause.AUTHORITY_CANCEL,
                -1.0f,
                10L);
        assertTrue(!OpinionLearningPolicy.accepts(blocked));
        memory.apply(blocked, 0L);
        assertEquals(0f, memory.preference(ActivityKind.REST));
    }

    @Test
    void repeatedExecutionFailuresGraduallyCreateNegativePreference() {
        OpinionMemory memory = new OpinionMemory();
        memory.apply(terminal(
                ActivityKind.TUNNEL_SEARCH,
                OutcomeClass.EXECUTION_FAILURE,
                ExperienceCause.MINING_NO_PROGRESS,
                -0.5f,
                200L), 200L);
        assertTrue(memory.preference(ActivityKind.TUNNEL_SEARCH) < 0f);
        assertEquals(1, memory.memoryOf(ActivityKind.TUNNEL_SEARCH).recentFailures());
    }

    @Test
    void pipelineWiringConsumesNormalizedEvidenceOnly() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        UUID episodeId = UUID.randomUUID();
        context.openEpisode(Optional.of(ActivityKind.REST), 0L);
        context.pipeline().accept(new ExperienceEvent(
                ExperienceKind.REST_SESSION,
                50L,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.REST_SESSION_CLOSE,
                0f, 0f, 0.2f, 0f, 0f,
                Optional.of(ActivityKind.REST),
                Optional.empty(),
                Optional.empty()));

        assertTrue(context.opinionMemory().preference(ActivityKind.REST) > 0f);
    }

    private static EpisodeLearningEvidence milestone(ActivityKind kind, float weight, long gameTime) {
        return new EpisodeLearningEvidence(
                EPISODE,
                Optional.of(kind),
                ExperienceKind.BLOCK_BROKEN,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.MINING_BLOCK_REMOVED,
                weight,
                gameTime);
    }

    private static EpisodeLearningEvidence terminal(
            ActivityKind kind,
            OutcomeClass outcome,
            ExperienceCause cause,
            float weight,
            long gameTime) {
        return new EpisodeLearningEvidence(
                EPISODE,
                Optional.of(kind),
                ExperienceKind.PROJECT_END,
                outcome,
                cause,
                weight,
                gameTime);
    }

    private static ExperienceEvent blockBroken(UUID episodeId, int index) {
        return new ExperienceEvent(
                ExperienceKind.BLOCK_BROKEN,
                index,
                episodeId,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.MINING_BLOCK_REMOVED,
                0.1f,
                0f,
                0f,
                0f,
                0f,
                Optional.of(ActivityKind.TUNNEL_SEARCH),
                Optional.empty(),
                Optional.empty());
    }
}
