package com.noobk.spmscavenger.opinion;

/**
 * GAO-4.1 — PD-GAO-01 C: mood modulates {@code exploreIdleTicks} when opinion is enabled.
 *
 * <p>High boredom lowers the idle threshold (expeditions unlock sooner). Opinion off ⇒ base ticks
 * unchanged (GAO-PARITY).
 */
public final class ExploreIdleThresholdPolicy {

    /** At maximum boredom, threshold is scaled by this fraction of base. */
    public static final float MIN_SCALE = 0.25f;
    /** Boredom channel contribution to scale reduction (0…1 of {@link #MIN_SCALE} headroom). */
    public static final float BOREDOM_SCALE_WEIGHT = 0.5f;
    public static final int ABSOLUTE_MIN_TICKS = 60;

    private ExploreIdleThresholdPolicy() {
    }

    public static int effectiveIdleTicks(int baseTicks, float boredom, boolean opinionEnabled) {
        int safeBase = Math.max(1, baseTicks);
        if (!opinionEnabled) {
            return safeBase;
        }
        float boredNorm = UtilityNormalizer.channel(boredom);
        float scale = 1.0f - boredNorm * BOREDOM_SCALE_WEIGHT * (1.0f - MIN_SCALE);
        int scaled = Math.round(safeBase * scale);
        return Math.max(ABSOLUTE_MIN_TICKS, Math.max(1, scaled));
    }
}
