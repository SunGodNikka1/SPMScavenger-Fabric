package com.noobk.spmscavenger.opinion;

import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GAO-5 — bounded per-mob place opinions keyed by chunk (soft utility only).
 */
public final class PlaceOpinionMemory {

    public static final int MAX_ENTRIES = 32;
    public static final float PREFERENCE_MIN = -100f;
    public static final float PREFERENCE_MAX = 100f;

    private final Map<Long, PlaceOpinionEntry> byChunk = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, PlaceOpinionEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public float preference(ChunkPos chunk) {
        return preference(chunk.toLong());
    }

    public float preference(long chunkKey) {
        PlaceOpinionEntry entry = byChunk.get(chunkKey);
        return entry == null ? 0f : entry.preference();
    }

    public void recordOutcome(long chunkKey, float preferenceDelta) {
        PlaceOpinionEntry entry = byChunk.computeIfAbsent(chunkKey, ignored -> new PlaceOpinionEntry());
        entry.addPreference(preferenceDelta);
    }

    public void clear() {
        byChunk.clear();
    }

    public int trackedPlaceCount() {
        return byChunk.size();
    }

    public Map<Long, Float> captureSnapshot() {
        Map<Long, Float> copy = new LinkedHashMap<>(byChunk.size());
        for (Map.Entry<Long, PlaceOpinionEntry> entry : byChunk.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().preference());
        }
        return copy;
    }

    public void restoreFromSnapshot(Map<Long, Float> snapshot) {
        byChunk.clear();
        for (Map.Entry<Long, Float> entry : snapshot.entrySet()) {
            PlaceOpinionEntry place = new PlaceOpinionEntry();
            place.restorePreference(entry.getValue());
            byChunk.put(entry.getKey(), place);
        }
    }

    static final class PlaceOpinionEntry {
        private float preference;

        float preference() {
            return preference;
        }

        void addPreference(float delta) {
            preference = Math.max(
                    PREFERENCE_MIN,
                    Math.min(PREFERENCE_MAX, preference + delta));
        }

        void restorePreference(float value) {
            preference = Math.max(PREFERENCE_MIN, Math.min(PREFERENCE_MAX, value));
        }
    }
}
