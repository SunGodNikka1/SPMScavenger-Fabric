package com.noobk.spmscavenger.village.intent;

import com.noobk.spmscavenger.village.routing.SettlementDestinationRanker;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime owner for at most one transient settlement commitment per loaded PlayerMob UUID.
 *
 * <p>Entries hold value identity only. Entity unload and death release their UUID; server stop
 * clears the map. No elapsed-time limit can invalidate an otherwise legitimate long trip.
 */
public final class VillageIntentRegistry {

    private static final Map<UUID, VillageIntent> INTENTS = new ConcurrentHashMap<>();

    private VillageIntentRegistry() {
    }

    /** Opens only when absent; a new ranking cannot steal a still-owned destination. */
    public static Optional<VillageIntent> openRequiredTrade(
            UUID mobId,
            VillageIntentFacts current,
            SettlementDestinationRanker.Selection selected,
            long now) {
        if (mobId == null) {
            return Optional.empty();
        }
        VillageIntent existing = INTENTS.get(mobId);
        if (existing != null) {
            VillageIntentEvaluation evaluation = VillageIntentPolicy.revalidate(existing, current);
            if (evaluation.intentStillExists()) {
                return Optional.of(existing);
            }
            INTENTS.remove(mobId, existing);
            // Closing and selecting are separate boundaries. The caller must deliberately open
            // again from current facts; this call cannot silently turn invalidation into retargeting.
            return Optional.empty();
        }
        Optional<VillageIntent> candidate =
                VillageIntentPolicy.openRequiredTrade(current, selected, now);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        VillageIntent raced = INTENTS.putIfAbsent(mobId, candidate.get());
        return Optional.ofNullable(raced == null ? candidate.get() : raced);
    }

    /** Re-reads legitimacy; invalid commitments are physically removed at this boundary. */
    public static VillageIntentEvaluation revalidate(UUID mobId, VillageIntentFacts current) {
        if (mobId == null) {
            return VillageIntentEvaluation.closed(VillageIntentEvaluation.Cause.NO_INTENT);
        }
        VillageIntent intent = INTENTS.get(mobId);
        if (intent == null) {
            return VillageIntentEvaluation.closed(VillageIntentEvaluation.Cause.NO_INTENT);
        }
        VillageIntentEvaluation evaluation = VillageIntentPolicy.revalidate(intent, current);
        if (!evaluation.intentStillExists()) {
            INTENTS.remove(mobId, intent);
        }
        return evaluation;
    }

    /**
     * Non-authoritative existence read for diagnostics and post-revalidation consumption.
     * Callers must not treat presence as current admission; execution must call
     * {@link #revalidate(UUID, VillageIntentFacts)} first.
     */
    public static Optional<VillageIntent> current(UUID mobId) {
        return mobId == null ? Optional.empty() : Optional.ofNullable(INTENTS.get(mobId));
    }

    public static void release(UUID mobId) {
        if (mobId != null) {
            INTENTS.remove(mobId);
        }
    }

    /** Releases travel ownership only when the exact still-current commitment arrived/failed. */
    public static synchronized boolean releaseIfCurrent(UUID mobId, VillageIntent expected) {
        if (mobId == null || expected == null) {
            return false;
        }
        if (INTENTS.get(mobId) != expected) {
            return false;
        }
        INTENTS.remove(mobId);
        return true;
    }

    public static void shutdownServerState() {
        INTENTS.clear();
    }

    /** Test/diagnostic. */
    public static int trackedIntentCount() {
        return INTENTS.size();
    }
}
