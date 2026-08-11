package com.noobk.spmscavenger.opinion;

import java.util.EnumMap;
import java.util.Map;

/** GAO-9 — enum-bounded long-term semantic environment affinity. */
public final class EnvironmentOpinionMemory {

    public static final float PREFERENCE_MIN = -100f;
    public static final float PREFERENCE_MAX = 100f;

    private final Map<EnvironmentKind, Float> preferences = new EnumMap<>(EnvironmentKind.class);

    public float preference(EnvironmentKind kind) {
        return preferences.getOrDefault(kind, 0f);
    }

    /** Applies one total evidence delta, divided so correlated labels cannot multiply learning. */
    public void recordOutcome(EnvironmentProfile profile, float totalDelta) {
        if (profile.isEmpty() || totalDelta == 0f) {
            return;
        }
        float perLabel = totalDelta / profile.size();
        for (EnvironmentKind kind : profile.kinds()) {
            preferences.compute(kind, (ignored, old) -> clamp((old == null ? 0f : old) + perLabel));
        }
    }

    public int trackedEnvironmentCount() {
        return preferences.size();
    }

    public Map<EnvironmentKind, Float> captureSnapshot() {
        return Map.copyOf(preferences);
    }

    public void restoreFromSnapshot(Map<EnvironmentKind, Float> snapshot) {
        preferences.clear();
        snapshot.forEach((kind, value) -> preferences.put(kind, clamp(value)));
    }

    private static float clamp(float value) {
        return Math.max(PREFERENCE_MIN, Math.min(PREFERENCE_MAX, value));
    }
}
