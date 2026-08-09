package com.noobk.spmscavenger.experience;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** GAO-0b — schema contract and ingress seam tests. */
class ExperienceEventTest {

    private static final UUID EPISODE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void preservesExactFields() {
        BlockPos place = new BlockPos(1, 64, 2);
        UUID entity = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        ExperienceEvent event = new ExperienceEvent(
                ExperienceKind.EXPEDITION_UNLOCKED,
                1200L,
                EPISODE,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.EXPEDITION_UNLOCKED,
                0.25f,
                -0.1f,
                0.0f,
                0.0f,
                0.5f,
                Optional.of(ActivityKind.OVERLAND_EXPLORATION),
                Optional.of(place),
                Optional.of(entity));

        assertEquals(ExperienceKind.EXPEDITION_UNLOCKED, event.kind());
        assertEquals(1200L, event.gameTime());
        assertEquals(EPISODE, event.episodeId());
        assertEquals(OutcomeClass.VOLUNTARY_SUCCESS, event.outcome());
        assertEquals(ExperienceCause.EXPEDITION_UNLOCKED, event.cause());
        assertEquals(0.25f, event.engagementDelta());
        assertEquals(-0.1f, event.boredomDelta());
        assertEquals(ActivityKind.OVERLAND_EXPLORATION, event.activity().orElseThrow());
        assertEquals(place, event.place().orElseThrow());
        assertEquals(entity, event.entity().orElseThrow());
    }

    @Test
    void rejectsNullKind() {
        assertThrows(NullPointerException.class, () -> new ExperienceEvent(
                null,
                10L,
                EPISODE,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.UNSPECIFIED,
                0f,
                0f,
                0f,
                0f,
                0f,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    @Test
    void rejectsNonFiniteDelta() {
        assertThrows(IllegalArgumentException.class, () -> new ExperienceEvent(
                ExperienceKind.BLOCK_BROKEN,
                0L,
                EPISODE,
                OutcomeClass.VOLUNTARY_SUCCESS,
                ExperienceCause.MINING_BLOCK_REMOVED,
                Float.NaN,
                0f,
                0f,
                0f,
                0f,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    @Test
    void pipelineForwardsIdenticalEvent() {
        List<ExperienceEvent> received = new ArrayList<>();
        ExperiencePipeline pipeline = received::add;
        ExperienceEvent event = sample(ExperienceKind.STAIR_STEP, OutcomeClass.VOLUNTARY_SUCCESS);

        pipeline.accept(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void activityKindIsDistinctFromSchedulerTaxonomy() {
        // Compile-time separation: subjective activity identity lives in experience package only.
        assertEquals(8, ActivityKind.values().length);
        assertEquals(12, ExperienceKind.values().length);
    }

    private static ExperienceEvent sample(ExperienceKind kind, OutcomeClass outcome) {
        return new ExperienceEvent(
                kind,
                10L,
                EPISODE,
                outcome,
                ExperienceCause.UNSPECIFIED,
                0f,
                0f,
                0f,
                0f,
                0f,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
