package com.noobk.spmscavenger.mining;

import net.minecraft.nbt.CompoundTag;

/**
 * MI-14C1 — the execution-lifecycle half of an assignment.
 *
 * <p>Kept beside {@link MiningProject} rather than inside it because they answer different
 * questions. The project is <em>what the dig is</em> — origin, heading, budget, route. The lease is
 * <em>whether anyone is running it</em>. Folding the second into the first would make every
 * lifecycle change rewrite dig state, and every dig update touch lifecycle timestamps.
 *
 * @param mode the assigned mode, so a lease cannot authorize an executor it was not issued to
 * @param assignedAt game time the director created the assignment
 * @param executorStartedAt game time the executor first began, or {@code 0} if it never has
 * @param state coarse lifecycle, for logging and for MI-14C3's stale detection
 */
public record MiningExecutionLease(
        MiningProjectMode mode, long assignedAt, long executorStartedAt, LeaseState state) {

    public enum LeaseState {
        /** Issued, never executed. Subject to the start lease. */
        ASSIGNED,
        /** The executor has run at least once. */
        ACTIVE,
        /** Blocked by something temporary; held for bounded recovery. */
        SUSPENDED
    }

    public static MiningExecutionLease issued(MiningProjectMode mode, long now) {
        return new MiningExecutionLease(mode, now, 0L, LeaseState.ASSIGNED);
    }

    public boolean everStarted() {
        return executorStartedAt > 0L;
    }

    public MiningExecutionLease started(long now) {
        return everStarted()
                ? this
                : new MiningExecutionLease(mode, assignedAt, now, LeaseState.ACTIVE);
    }

    public MiningExecutionLease suspended() {
        return state == LeaseState.SUSPENDED
                ? this
                : new MiningExecutionLease(mode, assignedAt, executorStartedAt,
                        LeaseState.SUSPENDED);
    }

    public MiningExecutionLease resumed() {
        LeaseState next = everStarted() ? LeaseState.ACTIVE : LeaseState.ASSIGNED;
        return state == next
                ? this
                : new MiningExecutionLease(mode, assignedAt, executorStartedAt, next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putLong("assignedAt", assignedAt);
        tag.putLong("startedAt", executorStartedAt);
        tag.putString("state", state.name());
        return tag;
    }

    public static MiningExecutionLease load(CompoundTag tag) {
        MiningProjectMode mode;
        try {
            mode = MiningProjectMode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException unknownMode) {
            mode = MiningProjectMode.CONTROLLED_DESCENT;
        }
        LeaseState state;
        try {
            state = LeaseState.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException unknownState) {
            state = LeaseState.ASSIGNED;
        }
        return new MiningExecutionLease(
                mode, tag.getLong("assignedAt"), tag.getLong("startedAt"), state);
    }
}
