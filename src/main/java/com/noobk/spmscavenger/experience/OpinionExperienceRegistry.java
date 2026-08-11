package com.noobk.spmscavenger.experience;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * GAO-0c — server-side per-mob experience contexts. Ephemeral REST claims invalidate on unload
 * (PD-GAO-07). RET-GAO-1 — live contexts park to a bounded frozen snapshot store on chunk unload.
 */
public final class OpinionExperienceRegistry {

    private static final ConcurrentMap<UUID, MobExperienceContext> LIVE_CONTEXTS = new ConcurrentHashMap<>();
    private static final FrozenContextStore FROZEN_SNAPSHOTS = new FrozenContextStore();
    private static volatile OpinionExperienceSinks sinks = OpinionExperienceSinks.noOp();

    private OpinionExperienceRegistry() {
    }

    public static void setSinks(OpinionExperienceSinks newSinks) {
        sinks = Objects.requireNonNull(newSinks, "newSinks");
    }

    public static void resetSinks() {
        sinks = OpinionExperienceSinks.noOp();
    }

    /**
     * PERF-5A — non-allocating lookup. Read/check paths must use this (or {@link #hasLiveRestClaim})
     * instead of {@link #contextFor(UUID)}.
     */
    @Nullable
    public static MobExperienceContext find(UUID mobId) {
        return LIVE_CONTEXTS.get(mobId);
    }

    /** PERF-5A — whether a live REST claim exists without creating a context. */
    public static boolean hasLiveRestClaim(UUID mobId) {
        MobExperienceContext context = LIVE_CONTEXTS.get(mobId);
        return context != null && context.hasLiveRestClaim();
    }

    /**
     * Allocates or rehydrates the per-mob experience context. Use only on experience-producing paths
     * (episode emission, discretionary rest open, opinion learning).
     */
    public static MobExperienceContext contextFor(UUID mobId) {
        MobExperienceContext live = LIVE_CONTEXTS.get(mobId);
        if (live != null) {
            return live;
        }
        return FROZEN_SNAPSHOTS.remove(mobId)
                .map(snapshot -> rehydrate(snapshot))
                .orElseGet(() -> createLive(mobId));
    }

    /**
     * RET-GAO-1 — park live context on chunk unload: snapshot learned state, discard heavyweight
     * execution state, bound retained entries via {@link FrozenContextStore}.
     */
    public static void parkOnUnload(UUID mobId, long gameTime) {
        evictExpiredFrozen(gameTime);
        MobExperienceContext context = LIVE_CONTEXTS.remove(mobId);
        if (context == null) {
            return;
        }
        context.prepareForUnloadPark();
        FROZEN_SNAPSHOTS.put(context.captureSnapshot(gameTime));
    }

    /**
     * RET-GAO-1 — promote a frozen snapshot back to a live context when the entity reloads.
     */
    public static void resumeOnLoad(UUID mobId) {
        if (LIVE_CONTEXTS.containsKey(mobId)) {
            return;
        }
        FROZEN_SNAPSHOTS.remove(mobId).ifPresent(OpinionExperienceRegistry::rehydrate);
    }

    public static void invalidateEphemeral(UUID mobId) {
        MobExperienceContext context = LIVE_CONTEXTS.get(mobId);
        if (context != null) {
            context.invalidateEphemeral();
        }
    }

    /**
     * @deprecated Unload uses {@link #parkOnUnload(UUID, long)}. Retained for tests that model
     *     in-chunk freeze without parking.
     */
    @Deprecated
    public static void freeze(UUID mobId) {
        MobExperienceContext context = LIVE_CONTEXTS.get(mobId);
        if (context != null) {
            context.freeze();
        }
    }

    public static void resume(UUID mobId) {
        MobExperienceContext context = LIVE_CONTEXTS.get(mobId);
        if (context != null) {
            context.resume();
        }
    }

    /** PD-GAO-03 — partial death reset for learned opinions (preference survives). */
    public static void onDeath(UUID mobId) {
        MobExperienceContext context = LIVE_CONTEXTS.get(mobId);
        if (context != null) {
            context.opinionMemory().onDeath();
            context.placeOpinionMemory().clear();
            context.entityOpinionMemory().clear();
            return;
        }
        FROZEN_SNAPSHOTS.remove(mobId).ifPresent(snapshot -> {
            MobExperienceContext scratch = new MobExperienceContext(snapshot.mobId(), sinks);
            scratch.restoreFromSnapshot(snapshot);
            scratch.opinionMemory().onDeath();
            scratch.placeOpinionMemory().clear();
            scratch.entityOpinionMemory().clear();
            FROZEN_SNAPSHOTS.put(scratch.captureSnapshot(snapshot.parkedAtGameTime()));
        });
    }

    /**
     * Gate RET-1 — production server-stop cleanup.
     */
    public static void shutdownServerState() {
        LIVE_CONTEXTS.clear();
        FROZEN_SNAPSHOTS.clear();
    }

    /** Reclaims completed episodes across every live context. Returns episodes released. */
    public static int compactClosedEpisodes() {
        int removed = 0;
        for (MobExperienceContext context : LIVE_CONTEXTS.values()) {
            removed += context.compactClosedEpisodes();
        }
        return removed;
    }

    /** Live contexts only — entities currently loaded with a heavyweight context. */
    public static int liveContextCount() {
        return LIVE_CONTEXTS.size();
    }

    /** Parked snapshots awaiting reload or LRU/TTL eviction. */
    public static int frozenSnapshotCount() {
        return FROZEN_SNAPSHOTS.size();
    }

    /**
     * Total retained mob identities (live + frozen). Bounded by {@link FrozenContextStore#MAX_SNAPSHOTS}
     * plus concurrently loaded mobs.
     */
    public static int contextCount() {
        return liveContextCount() + frozenSnapshotCount();
    }

    public static int evictExpiredFrozen(long gameTime) {
        return FROZEN_SNAPSHOTS.evictOlderThan(gameTime - FrozenContextStore.TTL_TICKS);
    }

    public static void remove(UUID mobId) {
        LIVE_CONTEXTS.remove(mobId);
        FROZEN_SNAPSHOTS.remove(mobId);
    }

    /** Test-only visibility. */
    public static void clearAll() {
        LIVE_CONTEXTS.clear();
        FROZEN_SNAPSHOTS.clear();
        resetSinks();
    }

    private static MobExperienceContext createLive(UUID mobId) {
        return LIVE_CONTEXTS.computeIfAbsent(mobId, id -> new MobExperienceContext(id, sinks));
    }

    private static MobExperienceContext rehydrate(MobExperienceSnapshot snapshot) {
        MobExperienceContext context = new MobExperienceContext(snapshot.mobId(), sinks);
        context.restoreFromSnapshot(snapshot);
        LIVE_CONTEXTS.put(snapshot.mobId(), context);
        return context;
    }
}
