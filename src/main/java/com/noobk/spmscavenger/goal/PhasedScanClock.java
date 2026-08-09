package com.noobk.spmscavenger.goal;

/**
 * Per-goal clock that distributes expensive scans by entity id.
 *
 * <p>GoalSelector does not promise to call {@code canUse()} on every game tick. A strict modulo
 * check can therefore miss an entity's slot forever. This clock treats the first poll at or after
 * the assigned slot as due, then schedules the next goal-specific phase.</p>
 */
final class PhasedScanClock {

    private static final long UNINITIALIZED = Long.MIN_VALUE;

    private final int interval;
    private final int phase;
    private long nextTick = UNINITIALIZED;

    PhasedScanClock(int entityId, int interval, int goalSalt) {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.interval = interval;
        this.phase = phaseFor(entityId, interval, goalSalt);
    }

    /** Claims this scan turn once; a late poll still succeeds instead of losing the phase forever. */
    boolean claim(long gameTick) {
        if (nextTick == UNINITIALIZED) {
            nextTick = atOrAfter(gameTick);
        }
        if (gameTick < nextTick) {
            return false;
        }
        nextTick = strictlyAfter(gameTick);
        return true;
    }

    /** Used when a completed/abandoned task must wait for its next assigned phase before rescanning. */
    void resetAfter(long gameTick) {
        nextTick = strictlyAfter(gameTick);
    }

    static int phaseFor(int entityId, int interval, int goalSalt) {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }
        return (int) Math.floorMod((long) entityId + goalSalt, interval);
    }

    private long atOrAfter(long tick) {
        int currentPhase = (int) Math.floorMod(tick, interval);
        int delay = Math.floorMod(phase - currentPhase, interval);
        return tick + delay;
    }

    private long strictlyAfter(long tick) {
        return atOrAfter(tick + 1L);
    }
}
