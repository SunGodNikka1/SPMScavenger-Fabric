package com.noobk.spmscavenger.village;

/**
 * Gen-1 timing and budget constants for the V1-D production perception driver (D-VR-033).
 *
 * <p>Provisional values — VR-T1 measures whether traversal through compact villages completes
 * before service at 1/10/50/100 mobs; they are not performance claims.
 */
public final class VillagePerceptionTuning {

    /** Maximum stationary refresh interval before a heartbeat re-dirties observation. */
    public static final int HEARTBEAT_TICKS = 200;

    /** Minimum gap between enqueue attempts after a denied or dirty request. */
    public static final int DEBOUNCE_TICKS = 20;

    /** Hard server-global POI query budget per server tick. */
    public static final int GLOBAL_QUERY_BUDGET_PER_TICK = 1;

    /**
     * Abnormal-only pending cap when the ticking-observer bound is exceeded. Normal posture is one
     * pending slot per registered observer.
     */
    public static final int MAX_EMERGENCY_PENDING = 256;

    /** Phased-scan salt so village heartbeat does not align with other goals on the same mob. */
    public static final int OBSERVER_GOAL_SALT = 0x56494C47; // "VILG"

    private VillagePerceptionTuning() {}
}
