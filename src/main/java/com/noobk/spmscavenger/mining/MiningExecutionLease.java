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
 * @param executorStartedAt game time the executor first began, or {@link #NEVER_STARTED}
 * @param currentBlocker the active blocking episode, or {@link ExecutionBlocker#NONE}
 * @param blockedSince game time the current blocking episode began, or {@link #NOT_BLOCKED}
 * @param state coarse lifecycle, for logging and for MI-14C3's stale detection
 */
public record MiningExecutionLease(
        MiningProjectMode mode,
        long assignedAt,
        long executorStartedAt,
        ExecutionBlocker currentBlocker,
        long blockedSince,
        LeaseState state) {

    /** Sentinel: the executor has never begun this assignment. */
    public static final long NEVER_STARTED = -1L;

    /** Sentinel: no blocking episode is in progress. */
    public static final long NOT_BLOCKED = -1L;

    private static final String NBT_V2 = "leaseV2";

    public enum LeaseState {
        /** Issued, never executed. Subject to the start lease. */
        ASSIGNED,
        /** The executor has run at least once. */
        ACTIVE,
        /** Blocked by something temporary; held for bounded recovery. */
        SUSPENDED
    }

    public static MiningExecutionLease issued(MiningProjectMode mode, long now) {
        return new MiningExecutionLease(
                mode, now, NEVER_STARTED, ExecutionBlocker.NONE, NOT_BLOCKED, LeaseState.ASSIGNED);
    }

    public boolean everStarted() {
        return executorStartedAt >= 0L;
    }

    /**
     * Records the current blocking episode. Clears {@link #blockedSince} when execution may resume;
     * starts a fresh episode clock when the blocker appears or changes.
     */
    public MiningExecutionLease recordBlocker(ExecutionBlocker blocker, long now) {
        if (blocker.permitsExecution()) {
            if (currentBlocker == ExecutionBlocker.NONE && blockedSince == NOT_BLOCKED) {
                return this;
            }
            return new MiningExecutionLease(
                    mode, assignedAt, executorStartedAt,
                    ExecutionBlocker.NONE, NOT_BLOCKED, state);
        }
        if (currentBlocker != blocker || blockedSince == NOT_BLOCKED) {
            return new MiningExecutionLease(
                    mode, assignedAt, executorStartedAt, blocker, now, state);
        }
        return this;
    }

    public MiningExecutionLease started(long now) {
        return everStarted()
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, now, currentBlocker, blockedSince, LeaseState.ACTIVE);
    }

    public MiningExecutionLease suspended() {
        return state == LeaseState.SUSPENDED
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, executorStartedAt,
                        currentBlocker, blockedSince, LeaseState.SUSPENDED);
    }

    public MiningExecutionLease resumed() {
        LeaseState next = everStarted() ? LeaseState.ACTIVE : LeaseState.ASSIGNED;
        return state == next
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, executorStartedAt,
                        currentBlocker, blockedSince, next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putLong("assignedAt", assignedAt);
        tag.putLong("startedAt", executorStartedAt);
        tag.putString("state", state.name());
        tag.putString("blocker", currentBlocker.name());
        tag.putLong("blockedSince", blockedSince);
        tag.putBoolean(NBT_V2, true);
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
        long startedAt = tag.getLong("startedAt");
        if (!tag.getBoolean(NBT_V2) && startedAt == 0L && state == LeaseState.ASSIGNED) {
            startedAt = NEVER_STARTED;
        }
        ExecutionBlocker blocker = ExecutionBlocker.NONE;
        long blockedSince = NOT_BLOCKED;
        if (tag.getBoolean(NBT_V2)) {
            try {
                blocker = ExecutionBlocker.valueOf(tag.getString("blocker"));
            } catch (IllegalArgumentException ignored) {
                blocker = ExecutionBlocker.NONE;
            }
            blockedSince = tag.getLong("blockedSince");
        }
        return new MiningExecutionLease(
                mode, tag.getLong("assignedAt"), startedAt, blocker, blockedSince, state);
    }
}
