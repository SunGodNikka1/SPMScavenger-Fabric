package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCR-2R5/R6 — loaded-mob ownership for a live nighttime shelter commitment.
 *
 * <p>The entry means "nighttime shelter owns this mob's voluntary-travel envelope". Ownership
 * begins when a reachable candidate is adopted, survives scheduler suspension and return travel,
 * becomes a stationary hold on exact settlement, and ends only when that correlated commitment is
 * cancelled. This closes the approach-time authority gap in which a lower-priority work goal could
 * start during a finite shelter interruption and then be repeatedly preempted by SeekShelter.
 *
 * <p>The map is bounded by loaded PlayerMobs with a live shelter commitment. The mob UUID is the
 * key; the commitment UUID prevents an obsolete lifecycle from releasing a newer authority.
 */
public final class ShelterNightAuthority {

    private static final Map<UUID, Hold> COMMITMENT_AUTHORITIES = new ConcurrentHashMap<>();

    public enum Phase { APPROACHING, SETTLED, RETURNING }

    public record Hold(
            UUID mobId,
            UUID commitmentId,
            BlockPos anchor,
            long phaseStartedAt,
            Phase phase) {
        public Hold {
            anchor = anchor.immutable();
        }
    }

    private ShelterNightAuthority() {
    }

    static void beginApproach(UUID mobId, UUID commitmentId, BlockPos anchor, long startedAt) {
        COMMITMENT_AUTHORITIES.put(mobId,
                new Hold(mobId, commitmentId, anchor, startedAt, Phase.APPROACHING));
    }

    static void acquire(UUID mobId, UUID commitmentId, BlockPos anchor, long arrivedAt) {
        COMMITMENT_AUTHORITIES.put(mobId,
                new Hold(mobId, commitmentId, anchor, arrivedAt, Phase.SETTLED));
    }

    static void markReturning(UUID mobId, UUID commitmentId) {
        COMMITMENT_AUTHORITIES.computeIfPresent(mobId, (ignored, hold) ->
                hold.commitmentId().equals(commitmentId)
                        ? new Hold(hold.mobId(), hold.commitmentId(), hold.anchor(),
                                hold.phaseStartedAt(), Phase.RETURNING)
                        : hold);
    }

    static boolean release(UUID mobId, UUID commitmentId) {
        return COMMITMENT_AUTHORITIES.computeIfPresent(mobId,
                (ignored, hold) -> hold.commitmentId().equals(commitmentId) ? null : hold) == null;
    }

    static void releaseOwner(UUID mobId) {
        COMMITMENT_AUTHORITIES.remove(mobId);
    }

    /** Optional SPM compatibility Mixins use this non-allocating read. */
    public static boolean holds(UUID mobId) {
        return COMMITMENT_AUTHORITIES.containsKey(mobId);
    }

    /** The old R4 door wrapper suppression applies only while physically settled, not returning. */
    public static boolean isSettled(UUID mobId) {
        Hold hold = COMMITMENT_AUTHORITIES.get(mobId);
        return hold != null && hold.phase() == Phase.SETTLED;
    }

    public static Optional<Hold> hold(UUID mobId) {
        return Optional.ofNullable(COMMITMENT_AUTHORITIES.get(mobId));
    }

    static int size() {
        return COMMITMENT_AUTHORITIES.size();
    }

    static void clear() {
        COMMITMENT_AUTHORITIES.clear();
    }
}
