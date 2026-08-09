package com.noobk.spmscavenger.experience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EpisodeRoutingPipelineTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID EPISODE = UUID.fromString("00000000-0000-0000-0000-0000000000cd");

    private final List<AffectPulse> pulses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        OpinionExperienceRegistry.setSinks(new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
                pulses.add(pulse);
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
            }
        });
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void routesEventsIntoEpisodeOwner() {
        EpisodeRoutingPipeline pipeline = OpinionExperienceRegistry.contextFor(MOB).pipeline();
        ExperienceEvent event = new ExperienceEvent(
                ExperienceKind.STAIR_STEP,
                5L,
                EPISODE,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.MINING_STAIR_STEP,
                0.2f,
                0f,
                0f,
                0f,
                0f,
                Optional.of(ActivityKind.CONTROLLED_DESCENT),
                Optional.empty(),
                Optional.empty());

        pipeline.accept(event);

        assertEquals(1, pulses.size());
        assertSame(EPISODE, pulses.get(0).episodeId());
        ActivityEpisode episode = OpinionExperienceRegistry.contextFor(MOB).episodeFor(EPISODE);
        assertEquals(ActivityKind.CONTROLLED_DESCENT, episode.activity().orElseThrow());
    }
}
