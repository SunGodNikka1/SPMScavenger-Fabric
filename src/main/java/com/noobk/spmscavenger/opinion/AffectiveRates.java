package com.noobk.spmscavenger.opinion;

/**
 * GAO-1 — tunable rate constants. Minute-scale boredom targets; validate in tests, not locked.
 */
public final class AffectiveRates {

    public static final float CHANNEL_MIN = -100f;
    public static final float CHANNEL_MAX = 100f;

    /** Observable progress from a pulse or active work within this window counts as recent. */
    public static final int PROGRESS_FRESHNESS_TICKS = 200;

    /**
     * Discretionary idle boredom per 10-tick observation (~0.12 → ~50 after ~4 min pure idle).
     */
    public static final float IDLE_BOREDOM_PER_OBSERVATION = 0.12f;

    /** PD-GAO-02 — REST boredom at 25% of idle rate. */
    public static final float REST_BOREDOM_PER_OBSERVATION = IDLE_BOREDOM_PER_OBSERVATION * 0.25f;

    /** Occupied-but-stalled restlessness; slower than discretionary idle. */
    public static final float STALLED_BOREDOM_PER_OBSERVATION = 0.045f;

    public static final float REST_STRESS_FALL_PER_OBSERVATION = 0.08f;
    public static final float REST_ENGAGEMENT_PER_OBSERVATION = 0.03f;
    public static final float SOCIAL_BOREDOM_RELIEF_PER_OBSERVATION = 0.05f;

    /** Per-observation decay toward neutral for channels not updated this step. */
    public static final float DECAY_PER_OBSERVATION = 0.02f;

    private AffectiveRates() {
    }
}
