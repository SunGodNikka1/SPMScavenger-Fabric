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

    /**
     * Personality traits ({@code 0..1}) — <b>not</b> a channel.
     *
     * <h2>Why this exists as its own name</h2>
     *
     * SOCIAL scoring passed {@code PersonalityModel.sociability()} through {@link #channel(float)},
     * which divides by {@code CHANNEL_MAX} because mood and opinion channels run {@code -100..+100}.
     * Personality traits are clamped to {@code [0, 1]} by {@code PersonalityModel.trait}, so the
     * division under-scaled them by <b>100x</b>:
     *
     * <pre>
     * a maximally Friendly mob   sociability = 1.0
     * channel(1.0) * 32          = 0.32        &lt;-- intended: 32
     * </pre>
     *
     * A mob displaying <i>Friendly</i> contributed about {@code +0.3} utility while a MEDIUM village
     * bias contributes {@code +12} — the village mattered dozens of times more than the mob's
     * defining personality trait. The weight said "personality is the strongest single term"; the
     * arithmetic said it was noise.
     *
     * <p>The unit is named rather than corrected by a stray {@code * 100} so the next trait added
     * cannot repeat it: a value that reaches scoring is either a {@link #channel} or a
     * {@link #trait01}, and the call site has to say which.
     */
    public static float trait01(float value) {
        return clamp01(value);
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
