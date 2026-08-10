package com.noobk.spmscavenger.experience;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RET-GAO-1 — bounded LRU + TTL store for {@link MobExperienceSnapshot} parked on chunk unload.
 *
 * <p>Stepping-stone until full persistence: evicted snapshots drop session learned state
 * intentionally.
 */
final class FrozenContextStore {

    static final int MAX_SNAPSHOTS = 128;
    /** 20 minutes — evict stale frozen snapshots that never reload. */
    static final long TTL_TICKS = 24_000L;

    private final Map<UUID, MobExperienceSnapshot> byMob = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, MobExperienceSnapshot> eldest) {
            return size() > MAX_SNAPSHOTS;
        }
    };

    void put(MobExperienceSnapshot snapshot) {
        byMob.put(snapshot.mobId(), snapshot);
    }

    Optional<MobExperienceSnapshot> remove(UUID mobId) {
        return Optional.ofNullable(byMob.remove(mobId));
    }

    Optional<MobExperienceSnapshot> peek(UUID mobId) {
        return Optional.ofNullable(byMob.get(mobId));
    }

    int size() {
        return byMob.size();
    }

    void clear() {
        byMob.clear();
    }

    int evictOlderThan(long cutoffGameTime) {
        int removed = 0;
        Iterator<Map.Entry<UUID, MobExperienceSnapshot>> entries = byMob.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().parkedAtGameTime() < cutoffGameTime) {
                entries.remove();
                removed++;
            }
        }
        return removed;
    }
}
