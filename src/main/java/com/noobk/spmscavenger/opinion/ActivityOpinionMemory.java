package com.noobk.spmscavenger.opinion;

/**
 * GAO-2 — per-{@link com.noobk.spmscavenger.experience.ActivityKind} learned memory.
 *
 * <p>{@code preference} is long-term liking; {@code repetition} is short-horizon "done too much
 * lately" and must be able to rise while preference stays positive.
 */
public final class ActivityOpinionMemory {

    private float preference;
    private float repetition;
    private float recentReward;
    private int recentFailures;
    private long lastPerformed;
    private long recentDuration;

    public float preference() {
        return preference;
    }

    public float repetition() {
        return repetition;
    }

    public float recentReward() {
        return recentReward;
    }

    public int recentFailures() {
        return recentFailures;
    }

    public long lastPerformed() {
        return lastPerformed;
    }

    public long recentDuration() {
        return recentDuration;
    }

    void setLastPerformed(long gameTime) {
        lastPerformed = Math.max(lastPerformed, gameTime);
    }

    void addPreference(float delta) {
        preference = clamp(preference + delta);
    }

    void addRepetition(float delta) {
        repetition = clamp(repetition + Math.max(0f, delta));
    }

    void addRecentReward(float delta) {
        if (delta > 0f) {
            recentReward = clamp(recentReward + delta);
        }
    }

    void recordFailure() {
        recentFailures = Math.min(recentFailures + 1, 1_000);
    }

    void setRecentDuration(long durationTicks) {
        recentDuration = Math.max(recentDuration, Math.max(0L, durationTicks));
    }

    void clearRepetition() {
        repetition = 0f;
    }

    void clearRecentDuration() {
        recentDuration = 0L;
    }

    void clearRecentFailures() {
        recentFailures = 0;
    }

    void decayRecentReward(float factor) {
        recentReward = clamp(recentReward * factor);
    }

    private static float clamp(float value) {
        return Math.max(OpinionMemory.CHANNEL_MIN, Math.min(OpinionMemory.CHANNEL_MAX, value));
    }

    /** Package-private test seam. */
    void seedForTest(float preference, float repetition, int recentFailures) {
        this.preference = clamp(preference);
        this.repetition = clamp(repetition);
        this.recentFailures = Math.max(0, recentFailures);
    }

    public Snapshot captureSnapshot() {
        return new Snapshot(
                preference,
                repetition,
                recentReward,
                recentFailures,
                lastPerformed,
                recentDuration);
    }

    public void restoreFromSnapshot(Snapshot snapshot) {
        preference = snapshot.preference();
        repetition = snapshot.repetition();
        recentReward = snapshot.recentReward();
        recentFailures = snapshot.recentFailures();
        lastPerformed = snapshot.lastPerformed();
        recentDuration = snapshot.recentDuration();
    }

    public record Snapshot(
            float preference,
            float repetition,
            float recentReward,
            int recentFailures,
            long lastPerformed,
            long recentDuration) {}
}
