package com.noobk.spmscavenger.village.work;

import java.util.concurrent.atomic.AtomicLong;

/** Session counters — diagnostics never grant permission. */
public final class VillageWorkFactsDiagnostics {

    private static final AtomicLong COMPLETE = new AtomicLong();
    private static final AtomicLong INCOMPLETE = new AtomicLong();
    private static final AtomicLong ANCHOR_INVALIDATIONS = new AtomicLong();

    private VillageWorkFactsDiagnostics() {}

    static void recordCompleteObservation() {
        COMPLETE.incrementAndGet();
    }

    static void recordIncompleteObservation() {
        INCOMPLETE.incrementAndGet();
    }

    static void recordAnchorInvalidation() {
        ANCHOR_INVALIDATIONS.incrementAndGet();
    }

    public static long completeObservations() {
        return COMPLETE.get();
    }

    public static long incompleteObservations() {
        return INCOMPLETE.get();
    }

    public static long anchorInvalidations() {
        return ANCHOR_INVALIDATIONS.get();
    }

    /** Test-only reset. */
    public static void resetForTest() {
        COMPLETE.set(0);
        INCOMPLETE.set(0);
        ANCHOR_INVALIDATIONS.set(0);
    }
}
