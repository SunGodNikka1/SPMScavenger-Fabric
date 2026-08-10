package com.noobk.spmscavenger.experience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityEpisodeTest {

    private static final UUID EPISODE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID MOB = EPISODE;

    private final List<AffectPulse> pulses = new ArrayList<>();
    private final List<EpisodeLearningEvidence> learning = new ArrayList<>();

    @BeforeEach
    void setUp() {
        OpinionExperienceRegistry.setSinks(new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
                pulses.add(pulse);
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
                learning.add(evidence);
            }
        });
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void eightBlockBrokenMilestonesProduceOneNormalizedLearningUpdate() {
        ActivityEpisode episode = new ActivityEpisode(
                EPISODE, Optional.of(ActivityKind.CONTROLLED_DESCENT), 0L);

        for (int i = 0; i < 8; i++) {
            episode.ingest(blockBroken(i), sinks(), context());
        }

        assertEquals(8, pulses.size());
        assertEquals(1, learning.size());
        assertEquals(ExperienceKind.BLOCK_BROKEN, learning.get(0).terminalKind());
        assertEquals(0.125f, learning.get(0).repetitionWeight(), 0.0001f);
    }

    @Test
    void simulationFrontierProducesAffectButNoPreferenceLearning() {
        ActivityEpisode episode = new ActivityEpisode(
                EPISODE, Optional.of(ActivityKind.OVERLAND_EXPLORATION), 0L);
        episode.ingest(new ExperienceEvent(
                ExperienceKind.EXPEDITION_UNLOCKED,
                10L,
                EPISODE,
                OutcomeClass.SIMULATION_FRONTIER,
                ExperienceCause.SIMULATION_FRONTIER,
                0.0f,
                0.1f,
                0.0f,
                0.0f,
                0.0f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.empty(),
                Optional.empty()), sinks(), context());

        assertEquals(1, pulses.size());
        assertTrue(learning.isEmpty());
        assertFalse(episode.isClosed());
    }

    @Test
    void executionFailureRequiresRepeatedEvidenceBeforeDislike() {
        MobExperienceContext context = context();
        ExperienceEvent failure = projectEnd(EPISODE, OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS);

        ActivityEpisode first = new ActivityEpisode(
                EPISODE, Optional.of(ActivityKind.TUNNEL_SEARCH), 0L);
        first.ingest(failure, sinks(), context);
        assertEquals(1, pulses.size());
        assertTrue(learning.isEmpty());

        UUID secondEpisode = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ActivityEpisode second = new ActivityEpisode(
                secondEpisode, Optional.of(ActivityKind.TUNNEL_SEARCH), 0L);
        second.ingest(
                projectEnd(secondEpisode, OutcomeClass.EXECUTION_FAILURE, ExperienceCause.MINING_NO_PROGRESS),
                sinks(),
                context);
        assertEquals(1, learning.size());
        assertTrue(learning.get(0).repetitionWeight() < 0.0f);
    }

    @Test
    void voluntaryAbandonSignDependsOnCause() {
        UUID restEpisode = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID socialEpisode = UUID.fromString("00000000-0000-0000-0000-000000000004");
        MobExperienceContext context = context();

        ActivityEpisode bored = new ActivityEpisode(restEpisode, Optional.of(ActivityKind.REST), 0L);
        bored.ingest(new ExperienceEvent(
                ExperienceKind.REST_SESSION,
                1L,
                restEpisode,
                OutcomeClass.VOLUNTARY_ABANDON,
                ExperienceCause.BOREDOM_THRESHOLD,
                0f, 0f, 0f, 0f, 0f,
                Optional.of(ActivityKind.REST),
                Optional.empty(),
                Optional.empty()), sinks(), context);

        ActivityEpisode social = new ActivityEpisode(socialEpisode, Optional.of(ActivityKind.SOCIALIZING), 0L);
        social.ingest(new ExperienceEvent(
                ExperienceKind.SOCIAL_INTERACTION,
                1L,
                socialEpisode,
                OutcomeClass.VOLUNTARY_ABANDON,
                ExperienceCause.SOCIAL_FOLLOW,
                0f, 0f, 0f, 0f, 0f,
                Optional.of(ActivityKind.SOCIALIZING),
                Optional.empty(),
                Optional.empty()), sinks(), context);

        assertTrue(learning.get(0).repetitionWeight() < 0.0f);
        assertTrue(learning.get(1).repetitionWeight() > 0.0f);
    }

    private OpinionExperienceSinks sinks() {
        return context().sinks();
    }

    private MobExperienceContext context() {
        return OpinionExperienceRegistry.contextFor(MOB);
    }

    private static ExperienceEvent projectEnd(UUID episodeId, OutcomeClass outcome, ExperienceCause cause) {
        return new ExperienceEvent(
                ExperienceKind.PROJECT_END,
                100L,
                episodeId,
                outcome,
                cause,
                0f,
                0f,
                0f,
                0.25f,
                0f,
                Optional.of(ActivityKind.TUNNEL_SEARCH),
                Optional.empty(),
                Optional.empty());
    }

    private static ExperienceEvent blockBroken(int index) {
        return new ExperienceEvent(
                ExperienceKind.BLOCK_BROKEN,
                index,
                EPISODE,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.MINING_BLOCK_REMOVED,
                0.1f,
                0f,
                0f,
                0f,
                0f,
                Optional.of(ActivityKind.CONTROLLED_DESCENT),
                Optional.empty(),
                Optional.empty());
    }
}

