package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.EpisodeLearningEvidence;
import com.noobk.spmscavenger.experience.EpisodeNormalizationPolicy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * GAO-2 — ACTIVITY-only learned opinions from normalized {@link EpisodeLearningEvidence}.
 */
public final class OpinionMemory {

    public static final float CHANNEL_MIN = -100f;
    public static final float CHANNEL_MAX = 100f;

    /** Preference learns slowly from normalized milestone windows. */
    public static final float MILESTONE_PREFERENCE_SCALE = 4f;
    /** Repetition accumulates faster than preference from the same milestones. */
    public static final float MILESTONE_REPETITION_SCALE = 24f;
    public static final float TERMINAL_PREFERENCE_SCALE = 12f;
    /** ~20 minutes of episode time counts as a long session for repetition pressure. */
    public static final long LONG_SESSION_TICKS = 24_000L;

    private final Map<ActivityKind, ActivityOpinionMemory> activities =
            new EnumMap<>(ActivityKind.class);

    public ActivityOpinionMemory memoryOf(ActivityKind kind) {
        return activities.computeIfAbsent(kind, ignored -> new ActivityOpinionMemory());
    }

    public float preference(ActivityKind kind) {
        return memoryOf(kind).preference();
    }

    public float repetition(ActivityKind kind) {
        return memoryOf(kind).repetition();
    }

    /**
     * Applies one normalized evidence record. Raw {@link com.noobk.spmscavenger.experience.ExperienceEvent}
     * records must never call this directly.
     */
    public void apply(EpisodeLearningEvidence evidence, long episodeDurationTicks) {
        apply(evidence, episodeDurationTicks, PersonalityLearningResponse.NEUTRAL);
    }

    /** GAO-7 subjective scaling; objective episode facts remain unchanged. */
    public void apply(
            EpisodeLearningEvidence evidence,
            long episodeDurationTicks,
            PersonalityLearningResponse personality) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(personality, "personality");
        if (!OpinionLearningPolicy.accepts(evidence)) {
            return;
        }
        ActivityKind kind = evidence.activity().orElseThrow();
        OpinionLearningPolicy.apply(memoryOf(kind), evidence, episodeDurationTicks, personality);
    }

    /**
     * PD-GAO-03 — partial death reset. Preference and {@code lastPerformed} survive; episodic
     * repetition and duration break; recent failures clear.
     */
    public void onDeath() {
        for (ActivityOpinionMemory memory : activities.values()) {
            memory.clearRepetition();
            memory.clearRecentDuration();
            memory.clearRecentFailures();
            memory.decayRecentReward(0.5f);
        }
    }

    int trackedActivityCount() {
        return activities.size();
    }

    /** Package-private test seam. */
    void seedActivity(ActivityKind kind, float preference, float repetition, int recentFailures) {
        memoryOf(kind).seedForTest(preference, repetition, recentFailures);
    }

    public Map<ActivityKind, ActivityOpinionMemory.Snapshot> captureSnapshot() {
        Map<ActivityKind, ActivityOpinionMemory.Snapshot> copy = new EnumMap<>(ActivityKind.class);
        for (Map.Entry<ActivityKind, ActivityOpinionMemory> entry : activities.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().captureSnapshot());
        }
        return copy;
    }

    public void restoreFromSnapshot(Map<ActivityKind, ActivityOpinionMemory.Snapshot> snapshot) {
        activities.clear();
        for (Map.Entry<ActivityKind, ActivityOpinionMemory.Snapshot> entry : snapshot.entrySet()) {
            ActivityOpinionMemory memory = new ActivityOpinionMemory();
            memory.restoreFromSnapshot(entry.getValue());
            activities.put(entry.getKey(), memory);
        }
    }
}
