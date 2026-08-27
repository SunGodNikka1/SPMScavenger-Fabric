package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.village.routing.RouteAttemptEvidence;
import com.noobk.spmscavenger.village.routing.SettlementKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V4-E transient physical-route failure history.
 *
 * <p>Key: loaded PlayerMob UUID. Bound: at most 16 settlement rows per mob. Expired rows are
 * physically pruned whenever ranking asks for a snapshot; unload/death releases the mob row and
 * server stop clears all. No entity, level, path, or semantic settlement memory is retained.
 */
final class VillageRouteAttemptRegistry {

    /** One existing exploration cooldown; enough to try another destination without blacklisting. */
    static final int DEMOTION_TICKS = 600;

    private static final Map<UUID, Map<SettlementKey, RouteAttemptEvidence.Attempt>> BY_MOB =
            new ConcurrentHashMap<>();

    private VillageRouteAttemptRegistry() {
    }

    static synchronized void recordTerminalFailure(UUID mobId, SettlementKey settlement, long now) {
        if (mobId == null || settlement == null) {
            return;
        }
        Map<SettlementKey, RouteAttemptEvidence.Attempt> attempts =
                BY_MOB.computeIfAbsent(mobId, ignored -> new HashMap<>());
        prune(attempts, now);
        RouteAttemptEvidence.Attempt prior = attempts.get(settlement);
        int generation = prior == null ? 1 : Math.min(
                RouteAttemptEvidence.MAX_FAILURE_GENERATION,
                prior.failureGeneration() + 1);
        attempts.put(settlement, new RouteAttemptEvidence.Attempt(
                settlement, now + DEMOTION_TICKS, generation));
        while (attempts.size() > RouteAttemptEvidence.MAX_ENTRIES) {
            SettlementKey victim = attempts.values().stream()
                    .min(Comparator
                            .comparingLong(RouteAttemptEvidence.Attempt::unavailableUntilTick)
                            .thenComparing(RouteAttemptEvidence.Attempt::settlement))
                    .orElseThrow()
                    .settlement();
            attempts.remove(victim);
        }
    }

    static synchronized RouteAttemptEvidence snapshot(UUID mobId, long now) {
        Map<SettlementKey, RouteAttemptEvidence.Attempt> attempts = BY_MOB.get(mobId);
        if (attempts == null) {
            return RouteAttemptEvidence.none();
        }
        prune(attempts, now);
        if (attempts.isEmpty()) {
            BY_MOB.remove(mobId, attempts);
            return RouteAttemptEvidence.none();
        }
        return RouteAttemptEvidence.of(new ArrayList<>(attempts.values()));
    }

    static synchronized void recordArrival(UUID mobId, SettlementKey settlement) {
        Map<SettlementKey, RouteAttemptEvidence.Attempt> attempts = BY_MOB.get(mobId);
        if (attempts == null) {
            return;
        }
        attempts.remove(settlement);
        if (attempts.isEmpty()) {
            BY_MOB.remove(mobId, attempts);
        }
    }

    static synchronized void release(UUID mobId) {
        if (mobId != null) {
            BY_MOB.remove(mobId);
        }
    }

    static synchronized void shutdownServerState() {
        BY_MOB.clear();
    }

    static synchronized int trackedMobCount() {
        return BY_MOB.size();
    }

    private static void prune(
            Map<SettlementKey, RouteAttemptEvidence.Attempt> attempts, long now) {
        attempts.values().removeIf(attempt -> now >= attempt.unavailableUntilTick());
    }
}
