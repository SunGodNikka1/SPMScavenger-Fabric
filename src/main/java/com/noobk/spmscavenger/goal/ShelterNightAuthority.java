package com.noobk.spmscavenger.goal;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCR-2R4 — loaded-mob ownership for a shelter commitment that is physically ARRIVED.
 *
 * <p>The entry means "SeekShelter currently owns this mob's stationary night hold", not merely
 * "the mob was once under a roof". It is opened on exact settlement and removed on displacement,
 * cancellation, unload, death, or server stop. The set is therefore bounded by loaded PlayerMobs
 * with a live arrived commitment; it is never saved and never keyed by a minted commitment ID.
 */
public final class ShelterNightAuthority {

    private static final Set<UUID> ARRIVED_HOLDS = ConcurrentHashMap.newKeySet();

    private ShelterNightAuthority() {
    }

    static void acquire(UUID mobId) {
        ARRIVED_HOLDS.add(mobId);
    }

    static void release(UUID mobId) {
        ARRIVED_HOLDS.remove(mobId);
    }

    /** Optional SPM compatibility Mixins use this non-allocating read. */
    public static boolean holds(UUID mobId) {
        return ARRIVED_HOLDS.contains(mobId);
    }

    static int size() {
        return ARRIVED_HOLDS.size();
    }

    static void clear() {
        ARRIVED_HOLDS.clear();
    }
}
