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
 * @param lastExecutionProgressAt last observable dig progress, or {@link #NO_PROGRESS_RECORDED}
 * @param progressPausedTicks blocker time excluded from the current progress window
 */
public record MiningExecutionLease(
        MiningProjectMode mode,
        long assignedAt,
        long executorStartedAt,
        ExecutionBlocker currentBlocker,
        long blockedSince,
        LeaseState state,
        long lastExecutionProgressAt,
        long progressPausedTicks) {

    /** Sentinel: the executor has never begun this assignment. */
    public static final long NEVER_STARTED = -1L;

    /** Sentinel: no blocking episode is in progress. */
    public static final long NOT_BLOCKED = -1L;

    /** Sentinel: starting an executor is not, by itself, observable dig progress. */
    public static final long NO_PROGRESS_RECORDED = -1L;

    private static final String NBT_V2 = "leaseV2";
    private static final String NBT_V3 = "leaseV3";

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
                mode, now, NEVER_STARTED, ExecutionBlocker.NONE, NOT_BLOCKED, LeaseState.ASSIGNED,
                NO_PROGRESS_RECORDED, 0L);
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
            long paused = settledProgressPause(now);
            return new MiningExecutionLease(
                    mode, assignedAt, executorStartedAt,
                    ExecutionBlocker.NONE, NOT_BLOCKED, state,
                    lastExecutionProgressAt, paused);
        }
        if (currentBlocker != blocker || blockedSince == NOT_BLOCKED) {
            long paused = settledProgressPause(now);
            return new MiningExecutionLease(
                    mode, assignedAt, executorStartedAt, blocker, now, state,
                    lastExecutionProgressAt, paused);
        }
        return this;
    }

    private long settledProgressPause(long now) {
        if (!everStarted() || blockedSince == NOT_BLOCKED || !pausesProgress(currentBlocker)) {
            return progressPausedTicks;
        }
        long episode = Math.max(0L, now - blockedSince);
        long room = Long.MAX_VALUE - progressPausedTicks;
        return progressPausedTicks + Math.min(episode, room);
    }

    private static boolean pausesProgress(ExecutionBlocker blocker) {
        return blocker.blockerClass() == ExecutionBlocker.BlockerClass.TEMPORARY
                || blocker.blockerClass() == ExecutionBlocker.BlockerClass.CONTENTION;
    }

    public MiningExecutionLease started(long now) {
        return everStarted()
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, now, currentBlocker, blockedSince, LeaseState.ACTIVE,
                        lastExecutionProgressAt, progressPausedTicks);
    }

    /** Records physical or terminal executor progress and starts a fresh progress window. */
    public MiningExecutionLease markProgress(long now) {
        return new MiningExecutionLease(
                mode, assignedAt, executorStartedAt, currentBlocker,
                blockedSince == NOT_BLOCKED ? NOT_BLOCKED : now,
                state, now, 0L);
    }

    public MiningExecutionLease suspended() {
        return state == LeaseState.SUSPENDED
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, executorStartedAt,
                        currentBlocker, blockedSince, LeaseState.SUSPENDED,
                        lastExecutionProgressAt, progressPausedTicks);
    }

    public MiningExecutionLease resumed() {
        LeaseState next = everStarted() ? LeaseState.ACTIVE : LeaseState.ASSIGNED;
        return state == next
                ? this
                : new MiningExecutionLease(
                        mode, assignedAt, executorStartedAt,
                        currentBlocker, blockedSince, next,
                        lastExecutionProgressAt, progressPausedTicks);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putLong("assignedAt", assignedAt);
        tag.putLong("startedAt", executorStartedAt);
        tag.putString("state", state.name());
        tag.putString("blocker", currentBlocker.name());
        tag.putLong("blockedSince", blockedSince);
        tag.putLong("lastProgressAt", lastExecutionProgressAt);
        tag.putLong("progressPausedTicks", progressPausedTicks);
        tag.putBoolean(NBT_V2, true);
        tag.putBoolean(NBT_V3, true);
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
        long lastProgressAt = NO_PROGRESS_RECORDED;
        long progressPausedTicks = 0L;
        if (tag.getBoolean(NBT_V3)) {
            lastProgressAt = tag.getLong("lastProgressAt");
            progressPausedTicks = Math.max(0L, tag.getLong("progressPausedTicks"));
        }
        return new MiningExecutionLease(
                mode, tag.getLong("assignedAt"), startedAt, blocker, blockedSince, state,
                lastProgressAt, progressPausedTicks);
    }
}
