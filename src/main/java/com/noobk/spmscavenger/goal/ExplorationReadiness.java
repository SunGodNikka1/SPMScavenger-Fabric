package com.noobk.spmscavenger.goal;

/**
 * Small, Minecraft-independent controller for deciding when ordinary idling should become an
 * expedition. Work and local movement report into the same controller, while the exploration goal
 * consumes its readiness when an expedition begins.
 */
public final class ExplorationReadiness {

    private int successfulLocalTrips;
    private int idleWorkTicks;
    private long cooldownUntilTick;
    /** MI-5: progression wants deep ore while local gather is ineligible. */
    private boolean descentPressure;

    public ExplorationReadiness() {
    }

    void recordMeaningfulWork() {
        successfulLocalTrips = 0;
        idleWorkTicks = 0;
    }

    void recordIdleTicks(int ticks) {
        idleWorkTicks = Math.max(0, idleWorkTicks + Math.max(0, ticks));
    }

    void recordSuccessfulLocalTrip() {
        successfulLocalTrips++;
    }

    void recordDescentPressure() {
        descentPressure = true;
    }

    void clearDescentPressure() {
        descentPressure = false;
    }

    boolean hasDescentPressure() {
        return descentPressure;
    }

    boolean eligible(long now, int localTripThreshold, int idleTickThreshold) {
        return now >= cooldownUntilTick
                && (descentPressure
                || successfulLocalTrips >= Math.max(1, localTripThreshold)
                || idleWorkTicks >= Math.max(1, idleTickThreshold));
    }

    void consume(long cooldownUntilTick) {
        successfulLocalTrips = 0;
        idleWorkTicks = 0;
        descentPressure = false;
        this.cooldownUntilTick = Math.max(this.cooldownUntilTick, cooldownUntilTick);
    }

    int successfulLocalTrips() {
        return successfulLocalTrips;
    }

    int idleWorkTicks() {
        return idleWorkTicks;
    }
}
