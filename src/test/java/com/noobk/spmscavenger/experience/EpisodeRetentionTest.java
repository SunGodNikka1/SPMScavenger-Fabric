package com.noobk.spmscavenger.experience;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate RET-1 — retained episode state must be bounded, and compaction must not resurrect learning.
 *
 * <p>The defect: {@code episodes} was a plain map with <b>no removal path at all</b>.
 * {@code openEpisode} mints a fresh {@code UUID.randomUUID()} per activity, {@code closed = true}
 * was a tombstone flag rather than an end of life, and {@code invalidateEphemeral} only suspended
 * open episodes. So one immortal PlayerMob doing ordinary activities grew its own map forever,
 * inside a static registry that itself never released contexts.
 *
 * <p>These tests run thousands of episodes because that is the only way to observe the property —
 * unit tests assert values, and this is about lifetimes.
 */
class EpisodeRetentionTest {

    @AfterEach
    void reset() {
        OpinionExperienceRegistry.clearAll();
    }

    private static UUID mob() {
        return UUID.randomUUID();
    }

    private static ActivityEpisode closedEpisode(MobExperienceContext context, long tick) {
        ActivityEpisode episode =
                context.openEpisode(Optional.of(ActivityKind.OVERLAND_EXPLORATION), tick);
        episode.forceCloseForTest();
        return episode;
    }

    @Test
    void mustHappen_thousandsOfCompletedEpisodesLeaveBoundedState() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());

        for (int i = 0; i < 5_000; i++) {
            closedEpisode(context, i);
            if (i % 100 == 0) {
                context.compactClosedEpisodes();
            }
        }
        context.compactClosedEpisodes();

        assertEquals(0, context.liveEpisodeCount(),
                "every episode completed, so nothing should still be retained as live");
        assertTrue(context.closedEpisodeTombstoneCount() <= 256,
                "tombstones are capped: identity is kept, the heavyweight object is not - "
                        + "retained " + context.closedEpisodeTombstoneCount());
    }

    /** A long-running or suspended episode is live state and must survive compaction. */
    @Test
    void mustNotHappen_anActiveEpisodeIsEvictedForBeingOld() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());
        ActivityEpisode longRunning =
                context.openEpisode(Optional.of(ActivityKind.CAVE_EXPLORATION), 0L);

        for (int i = 0; i < 1_000; i++) {
            closedEpisode(context, 100 + i);
        }
        context.compactClosedEpisodes();

        assertEquals(1, context.liveEpisodeCount(),
                "an LRU over the whole map would have discarded an activity still in progress - "
                        + "that trades a memory bug for a behaviour bug");
        assertSame(longRunning, context.findEpisode(longRunning.episodeId()).orElseThrow());
    }

    @Test
    void mustHappen_suspendedEpisodesSurviveInvalidation() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());
        ActivityEpisode open = context.openEpisode(Optional.of(ActivityKind.REST), 0L);
        closedEpisode(context, 1L);

        context.invalidateEphemeral();

        assertEquals(1, context.liveEpisodeCount(),
                "invalidation suspends rather than ends, so only the finished episode is reclaimed");
        assertTrue(context.findEpisode(open.episodeId()).isPresent());
    }

    /** The correctness half: compaction must not make a late event learnable again. */
    @Test
    void mustNotHappen_aLateEventResurrectsACompletedEpisode() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());
        ActivityEpisode episode = closedEpisode(context, 10L);
        UUID id = episode.episodeId();

        context.compactClosedEpisodes();
        assertTrue(context.hasCompletedEpisode(id), "identity retained as a tombstone");
        assertEquals(0, context.liveEpisodeCount(), "object released");

        ActivityEpisode late =
                context.ensureEpisode(id, 10L, Optional.of(ActivityKind.OVERLAND_EXPLORATION));

        assertTrue(late.isClosed(),
                "a delayed duplicate must still be swallowed by the closed guard - rebuilding a "
                        + "live episode here would relearn from an event already accounted for");
        assertEquals(0, context.liveEpisodeCount(),
                "and the stand-in must not re-enter the map, or the leak returns one event at a "
                        + "time");
    }

    @Test
    void mustNotHappen_theDeprecatedAccessorResurrectsOne() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());
        UUID id = closedEpisode(context, 5L).episodeId();
        context.compactClosedEpisodes();

        assertTrue(context.episodeFor(id).isClosed(),
                "every entry point into the map has to honour the tombstone, not just the new one");
        assertEquals(0, context.liveEpisodeCount());
    }

    // ---- registry lifetime ----

    @Test
    void mustHappen_serverStopLeavesNoContexts() {
        for (int i = 0; i < 50; i++) {
            closedEpisode(OpinionExperienceRegistry.contextFor(mob()), i);
        }
        assertEquals(50, OpinionExperienceRegistry.contextCount());

        OpinionExperienceRegistry.shutdownServerState();

        assertEquals(0, OpinionExperienceRegistry.contextCount(),
                "leaving a world must release its population - in singleplayer the JVM outlives "
                        + "the integrated server, so world A stays reachable otherwise");
    }

    @Test
    void mustHappen_compactionAcrossEveryRetainedContext() {
        for (int i = 0; i < 20; i++) {
            MobExperienceContext context = OpinionExperienceRegistry.contextFor(mob());
            for (int e = 0; e < 10; e++) {
                closedEpisode(context, e);
            }
        }

        assertEquals(200, OpinionExperienceRegistry.compactClosedEpisodes(),
                "one sweep reclaims completed episodes for the whole population");
        assertEquals(0, OpinionExperienceRegistry.compactClosedEpisodes(),
                "and a second sweep finds nothing - compaction is idempotent, not repeated work");
    }

    /**
     * Recorded, not fixed. PD-GAO-03 says preference survives death, and the death hook resets
     * opinion rather than deleting the context. Evicting on death would make "preference survives"
     * last exactly one method call, so identity semantics must be settled first.
     */
    @Test
    void documentsThatDeathDoesNotEvictTheContext() {
        UUID mobId = mob();
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(mobId);
        closedEpisode(context, 1L);

        OpinionExperienceRegistry.onDeath(mobId);

        assertEquals(1, OpinionExperienceRegistry.contextCount(),
                "death resets learned state per PD-GAO-03; it must not silently destroy it. When "
                        + "PlayerMob identity semantics are settled, update this test rather than "
                        + "deleting it");
        assertFalse(OpinionExperienceRegistry.contextFor(mobId) == null);
    }
}
