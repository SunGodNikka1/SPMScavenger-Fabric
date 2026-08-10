package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.OpinionMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate RET-GAO-1 — {@link OpinionExperienceRegistry} must not grow without bound across unique mob
 * load/unload cycles. Learned opinions must survive temporary unload for the same identity.
 */
class OpinionExperienceRegistryRetentionTest {

    @AfterEach
    void reset() {
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void mustHappen_repeatedUniqueUnloadCyclesStayBounded() {
        for (int i = 0; i < 500; i++) {
            UUID mobId = UUID.randomUUID();
            MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
            context.openEpisode(Optional.of(ActivityKind.OVERLAND_EXPLORATION), i);
            OpinionExperienceRegistry.parkOnUnload(mobId, i);
        }

        assertTrue(
                OpinionExperienceRegistry.frozenSnapshotCount() <= FrozenContextStore.MAX_SNAPSHOTS,
                "frozen store capped at " + FrozenContextStore.MAX_SNAPSHOTS + " but retained "
                        + OpinionExperienceRegistry.frozenSnapshotCount());
        assertEquals(0, OpinionExperienceRegistry.liveContextCount(),
                "parked contexts must not remain live");
        assertTrue(
                OpinionExperienceRegistry.contextCount() <= FrozenContextStore.MAX_SNAPSHOTS,
                "total retained identities must not exceed frozen cap when nothing is loaded");
    }

    @Test
    void mustHappen_learnedOpinionSurvivesUnloadAndReload() {
        UUID mobId = UUID.randomUUID();
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        context.opinionMemory().apply(
                new EpisodeLearningEvidence(
                        UUID.randomUUID(),
                        Optional.of(ActivityKind.REST),
                        ExperienceKind.PROJECT_END,
                        OutcomeClass.VOLUNTARY_SUCCESS,
                        ExperienceCause.REST_SESSION_CLOSE,
                        1f,
                        10L),
                100L);
        context.placeOpinionMemory().recordOutcome(0xBEEFL, 7f);

        OpinionExperienceRegistry.parkOnUnload(mobId, 100L);
        assertNull(OpinionExperienceRegistry.find(mobId), "live context discarded on park");

        OpinionExperienceRegistry.resumeOnLoad(mobId);
        MobExperienceContext reloaded = OpinionExperienceRegistry.contextFor(mobId);

        assertTrue(reloaded.opinionMemory().preference(ActivityKind.REST) > 0f);
        assertEquals(7f, reloaded.placeOpinionMemory().preference(0xBEEFL), 0.001f);
        assertEquals(0, reloaded.liveEpisodeCount(),
                "suspended episodes must not survive park");
    }

    @Test
    void mustHappen_suspendedEpisodeAbandonedOnPark() {
        UUID mobId = UUID.randomUUID();
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);
        context.invalidateEphemeral();

        OpinionExperienceRegistry.parkOnUnload(mobId, 1L);

        OpinionExperienceRegistry.resumeOnLoad(mobId);
        assertEquals(
                0,
                OpinionExperienceRegistry.contextFor(mobId).liveEpisodeCount(),
                "abandoned suspended episodes must not resume as live state");
    }

    @Test
    void mustHappen_lruEvictionDropsOldestFrozenSnapshot() {
        UUID first = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        OpinionExperienceRegistry.contextFor(first).opinionMemory().apply(
                new EpisodeLearningEvidence(
                        UUID.randomUUID(),
                        Optional.of(ActivityKind.REST),
                        ExperienceKind.PROJECT_END,
                        OutcomeClass.VOLUNTARY_SUCCESS,
                        ExperienceCause.REST_SESSION_CLOSE,
                        1f,
                        1L),
                100L);
        OpinionExperienceRegistry.parkOnUnload(first, 1L);

        for (int i = 0; i < FrozenContextStore.MAX_SNAPSHOTS; i++) {
            UUID filler = UUID.randomUUID();
            OpinionExperienceRegistry.contextFor(filler);
            OpinionExperienceRegistry.parkOnUnload(filler, 2L + i);
        }
        OpinionExperienceRegistry.contextFor(last);
        OpinionExperienceRegistry.parkOnUnload(last, 10_000L);

        assertEquals(FrozenContextStore.MAX_SNAPSHOTS, OpinionExperienceRegistry.frozenSnapshotCount());
        OpinionExperienceRegistry.resumeOnLoad(first);
        assertNull(OpinionExperienceRegistry.find(first), "oldest frozen snapshot evicted by LRU");

        OpinionExperienceRegistry.resumeOnLoad(last);
        assertNotNull(OpinionExperienceRegistry.find(last), "newest snapshot survives LRU cap");
    }

    @Test
    void mustHappen_ttlEvictionRemovesStaleSnapshots() {
        UUID mobId = UUID.randomUUID();
        OpinionExperienceRegistry.contextFor(mobId);
        OpinionExperienceRegistry.parkOnUnload(mobId, 100L);

        int removed = OpinionExperienceRegistry.evictExpiredFrozen(
                100L + FrozenContextStore.TTL_TICKS + 1L);
        assertEquals(1, removed);
        assertEquals(0, OpinionExperienceRegistry.frozenSnapshotCount());
    }
}
