package com.noobk.spmscavenger.opinion;

import java.util.Objects;

/**
 * GAO-8B — last-tick explore adoption diagnostics copied from {@link
 * com.noobk.spmscavenger.goal.ExplorationReadiness} for inspector readout.
 */
public record ExploreReadinessSnapshot(
        int idleWorkTicks,
        int idleTickThreshold,
        int successfulLocalTrips,
        int localTripThreshold,
        long cooldownRemainingTicks,
        boolean descentPressure,
        boolean adoptionReady,
        String blockerDetail) {

    private static final ExploreReadinessSnapshot EMPTY = new ExploreReadinessSnapshot(
            0, 0, 0, 0, 0L, false, false, "");

    public ExploreReadinessSnapshot {
        blockerDetail = blockerDetail == null ? "" : blockerDetail;
    }

    public static ExploreReadinessSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public static ExploreReadinessSnapshot of(
            int idleWorkTicks,
            int idleTickThreshold,
            int successfulLocalTrips,
            int localTripThreshold,
            long now,
            long cooldownUntilTick,
            boolean descentPressure,
            boolean adoptionReady,
            String blockerDetail) {
        long cooldownRemaining = Math.max(0L, cooldownUntilTick - now);
        return new ExploreReadinessSnapshot(
                idleWorkTicks,
                idleTickThreshold,
                successfulLocalTrips,
                localTripThreshold,
                cooldownRemaining,
                descentPressure,
                adoptionReady,
                Objects.requireNonNullElse(blockerDetail, ""));
    }
}
