package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCR-2R4 — loaded-mob ownership for a shelter commitment that is physically ARRIVED.
 *
 * <p>The entry means "SeekShelter currently owns this mob's stationary night hold", not merely
 * "the mob was once under a roof". It is opened on exact settlement and removed on displacement,
 * cancellation, unload, death, or server stop. The map is therefore bounded by loaded PlayerMobs
 * with a live arrived commitment. The mob UUID is the key; the commitment UUID is correlation data
 * that prevents an obsolete lifecycle from releasing a newer hold.
 */
public final class ShelterNightAuthority {

    private static final Map<UUID, Hold> ARRIVED_HOLDS = new ConcurrentHashMap<>();

    public enum Phase { SETTLED, RETURNING }

    public record Hold(
            UUID mobId,
            UUID commitmentId,
            BlockPos anchor,
            long arrivedAt,
            Phase phase) {
        public Hold {
            anchor = anchor.immutable();
        }
    }

    private ShelterNightAuthority() {
    }

    static void acquire(UUID mobId, UUID commitmentId, BlockPos anchor, long arrivedAt) {
        ARRIVED_HOLDS.put(mobId,
                new Hold(mobId, commitmentId, anchor, arrivedAt, Phase.SETTLED));
    }

    static void markReturning(UUID mobId, UUID commitmentId) {
        ARRIVED_HOLDS.computeIfPresent(mobId, (ignored, hold) ->
                hold.commitmentId().equals(commitmentId)
                        ? new Hold(hold.mobId(), hold.commitmentId(), hold.anchor(),
                                hold.arrivedAt(), Phase.RETURNING)
                        : hold);
    }

    static boolean release(UUID mobId, UUID commitmentId) {
        return ARRIVED_HOLDS.computeIfPresent(mobId,
                (ignored, hold) -> hold.commitmentId().equals(commitmentId) ? null : hold) == null;
    }

    static void releaseOwner(UUID mobId) {
        ARRIVED_HOLDS.remove(mobId);
    }

    /** Optional SPM compatibility Mixins use this non-allocating read. */
    public static boolean holds(UUID mobId) {
        return ARRIVED_HOLDS.containsKey(mobId);
    }

    /** The old R4 door wrapper suppression applies only while physically settled, not returning. */
    public static boolean isSettled(UUID mobId) {
        Hold hold = ARRIVED_HOLDS.get(mobId);
        return hold != null && hold.phase() == Phase.SETTLED;
    }

    public static Optional<Hold> hold(UUID mobId) {
        return Optional.ofNullable(ARRIVED_HOLDS.get(mobId));
    }

    static int size() {
        return ARRIVED_HOLDS.size();
    }

    static void clear() {
        ARRIVED_HOLDS.clear();
    }
}
