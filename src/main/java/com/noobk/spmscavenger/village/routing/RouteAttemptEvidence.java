package com.noobk.spmscavenger.village.routing;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * D-VR-093 — bounded immutable ranking input for temporary destination demotion.
 *
 * <p>V4-C defines consumption only. A future movement owner may produce a fresh value; this class
 * contains no history writer and never becomes semantic settlement memory.
 */
public final class RouteAttemptEvidence {

    /** One dimension-local memory contains at most this many destination candidates. */
    public static final int MAX_ENTRIES = 16;
    public static final int MAX_FAILURE_GENERATION = 8;

    public record Attempt(
            SettlementKey settlement, long unavailableUntilTick, int failureGeneration) {
        public Attempt {
            settlement = Objects.requireNonNull(settlement, "settlement");
            if (failureGeneration < 0 || failureGeneration > MAX_FAILURE_GENERATION) {
                throw new IllegalArgumentException(
                        "failureGeneration outside bounds: " + failureGeneration);
            }
        }
    }

    private final List<Attempt> attempts;

    private RouteAttemptEvidence(List<Attempt> attempts) {
        this.attempts = List.copyOf(attempts);
    }

    public static RouteAttemptEvidence none() {
        return new RouteAttemptEvidence(List.of());
    }

    public static RouteAttemptEvidence of(Collection<Attempt> attempts) {
        Objects.requireNonNull(attempts, "attempts");
        if (attempts.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many route-attempt entries: " + attempts.size());
        }
        TreeMap<SettlementKey, Attempt> bySettlement = new TreeMap<>();
        for (Attempt attempt : attempts) {
            Objects.requireNonNull(attempt, "attempt");
            bySettlement.merge(attempt.settlement(), attempt, RouteAttemptEvidence::newer);
        }
        return new RouteAttemptEvidence(List.copyOf(bySettlement.values()));
    }

    public boolean temporarilyUnavailable(SettlementKey settlement, long now) {
        return attempts.stream().anyMatch(attempt -> attempt.settlement().equals(settlement)
                && now < attempt.unavailableUntilTick());
    }

    public int size() {
        return attempts.size();
    }

    private static Attempt newer(Attempt left, Attempt right) {
        int expiry = Long.compare(left.unavailableUntilTick(), right.unavailableUntilTick());
        if (expiry != 0) {
            return expiry > 0 ? left : right;
        }
        return left.failureGeneration() >= right.failureGeneration() ? left : right;
    }
}
