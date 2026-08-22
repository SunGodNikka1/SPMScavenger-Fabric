package com.noobk.spmscavenger.village.compost;

/**
 * Provisional tuning for V3-F opportunistic compost episodes (task-58).
 */
public final class CompostTuning {

    public static final int INTERACT_PREPARE_TICKS = 2;
    public static final int PATH_TIMEOUT_TICKS = 100;
    public static final double REACH_DISTANCE_SQR = 4.0;

    public static final int EMPTY_SCAN_COOLDOWN = 40;
    public static final int POST_EPISODE_COOLDOWN_TICKS = 200;

    /** Bounded composter path probes per settlement selection. */
    public static final int MAX_COMPOSTER_CANDIDATES = 8;

    private CompostTuning() {}
}
