package com.noobk.spmscavenger.opinion;

/**
 * GAO-3 — maps heterogeneous opinion/mood channels onto a shared {@code [-1, +1]} component scale
 * before weighted summation.
 */
public final class UtilityNormalizer {

    /** Failures saturate at this count for normalization. */
    public static final int FAILURE_SATURATION_COUNT = 5;

    private UtilityNormalizer() {
    }

    /** Mood and preference channels ({@code -100..+100}). */
    public static float channel(float value) {
        return clamp(value / OpinionMemory.CHANNEL_MAX);
    }

    /** Repetition pressure ({@code 0..+100}). */
    public static float repetitionPressure(float repetition) {
        return clamp01(repetition / OpinionMemory.CHANNEL_MAX);
    }

    /** Recent failure count ({@code 0..FAILURE_SATURATION_COUNT} → {@code 0..1}). */
    public static float failurePressure(int recentFailures) {
        return clamp01(recentFailures / (float) FAILURE_SATURATION_COUNT);
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
