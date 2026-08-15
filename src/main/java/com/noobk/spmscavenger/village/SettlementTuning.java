package com.noobk.spmscavenger.village;

/**
 * V1.5 gen-1 accumulation and commute tuning (D-VR-036). VR-T1.5b may adjust constants.
 */
public final class SettlementTuning {

    public static final int MAX_FAMILIARITY = 1000;
    public static final int MEDIUM_BAND_MIN = 200;
    public static final int HIGH_BAND_MIN = 600;

    public static final int VISIT_STALE_TICKS = 200;
    public static final int VISIT_FAMILIARITY_BUMP = 25;

    public static final int PRESENCE_HEARTBEAT_TICKS = 200;
    public static final int PRESENCE_FAMILIARITY_BUMP = 1;

    public static final int SOCIAL_FAMILIARITY_BUMP = 40;
    public static final int MAX_SOCIAL_EVENT_COUNT = 10_000;

    public static final int HOME_DESIGNATION_FAMILIARITY_FLOOR = MEDIUM_BAND_MIN;

    public static final double COMMUTE_MIN_DISTANCE = 128.0;

    /** Bounded SOCIAL utility bump when MEDIUM+ inside settlement bounds (D-VR-045). */
    public static final float SETTLEMENT_SOCIAL_BIAS_MEDIUM = 12f;
    public static final float SETTLEMENT_SOCIAL_BIAS_HIGH = 24f;

    private SettlementTuning() {
    }
}
