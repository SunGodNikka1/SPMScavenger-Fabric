package com.noobk.spmscavenger.experience;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * GAO-0c — server-side per-mob experience contexts. Ephemeral REST claims invalidate on unload
 * (PD-GAO-07).
 */
public final class OpinionExperienceRegistry {

    private static final ConcurrentMap<UUID, MobExperienceContext> CONTEXTS = new ConcurrentHashMap<>();
    private static volatile OpinionExperienceSinks sinks = OpinionExperienceSinks.noOp();

    private OpinionExperienceRegistry() {
    }

    public static void setSinks(OpinionExperienceSinks newSinks) {
        sinks = Objects.requireNonNull(newSinks, "newSinks");
    }

    public static void resetSinks() {
        sinks = OpinionExperienceSinks.noOp();
    }

    public static MobExperienceContext contextFor(UUID mobId) {
        return CONTEXTS.computeIfAbsent(mobId, id -> new MobExperienceContext(id, sinks));
    }

    public static void invalidateEphemeral(UUID mobId) {
        MobExperienceContext context = CONTEXTS.get(mobId);
        if (context != null) {
            context.invalidateEphemeral();
        }
    }

    public static void freeze(UUID mobId) {
        MobExperienceContext context = CONTEXTS.get(mobId);
        if (context != null) {
            context.freeze();
        }
    }

    public static void resume(UUID mobId) {
        MobExperienceContext context = CONTEXTS.get(mobId);
        if (context != null) {
            context.resume();
        }
    }

    /** PD-GAO-03 — partial death reset for learned opinions (preference survives). */
    public static void onDeath(UUID mobId) {
        MobExperienceContext context = CONTEXTS.get(mobId);
        if (context != null) {
            context.opinionMemory().onDeath();
        }
    }

    /**
     * Gate RET-1 — production server-stop cleanup.
     *
     * <p>Deliberately separate from {@link #clearAll()}, which is test-only and also resets sink
     * configuration. This releases per-world state without touching injection wiring.
     *
     * <p>Matters in singleplayer without ever quitting the game: open world A, leave, open world B,
     * and world A's contexts are still reachable through this static map.
     */
    public static void shutdownServerState() {
        CONTEXTS.clear();
    }

    /** Reclaims completed episodes across every retained context. Returns episodes released. */
    public static int compactClosedEpisodes() {
        int removed = 0;
        for (MobExperienceContext context : CONTEXTS.values()) {
            removed += context.compactClosedEpisodes();
        }
        return removed;
    }

    public static int contextCount() {
        return CONTEXTS.size();
    }

    public static void remove(UUID mobId) {
        CONTEXTS.remove(mobId);
    }

    /** Test-only visibility. */
    public static void clearAll() {
        CONTEXTS.clear();
        resetSinks();
    }
}
