package com.noobk.spmscavenger.opinion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GAO-6 — bounded per-mob learned entity affinity (supplemental to SPM {@code feelingToward}).
 *
 * <p>SPM owns the relationship graph. This memory records contextual experience only and must never
 * replace or override SPM social authority.
 */
public final class EntityOpinionMemory {

    public static final int MAX_ENTRIES = 16;
    public static final float PREFERENCE_MIN = -100f;
    public static final float PREFERENCE_MAX = 100f;

    private final Map<UUID, EntityOpinionEntry> byEntity = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, EntityOpinionEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public float preference(UUID entityId) {
        EntityOpinionEntry entry = byEntity.get(entityId);
        return entry == null ? 0f : entry.preference();
    }

    public void recordOutcome(UUID entityId, float preferenceDelta) {
        if (entityId == null) {
            return;
        }
        EntityOpinionEntry entry = byEntity.computeIfAbsent(entityId, ignored -> new EntityOpinionEntry());
        entry.addPreference(preferenceDelta);
    }

    public void clear() {
        byEntity.clear();
    }

    public int trackedEntityCount() {
        return byEntity.size();
    }

    public Map<UUID, Float> captureSnapshot() {
        Map<UUID, Float> copy = new LinkedHashMap<>(byEntity.size());
        for (Map.Entry<UUID, EntityOpinionEntry> entry : byEntity.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().preference());
        }
        return copy;
    }

    public void restoreFromSnapshot(Map<UUID, Float> snapshot) {
        byEntity.clear();
        for (Map.Entry<UUID, Float> entry : snapshot.entrySet()) {
            EntityOpinionEntry entity = new EntityOpinionEntry();
            entity.restorePreference(entry.getValue());
            byEntity.put(entry.getKey(), entity);
        }
    }

    static final class EntityOpinionEntry {
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
