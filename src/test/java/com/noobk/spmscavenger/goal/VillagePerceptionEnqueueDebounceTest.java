package com.noobk.spmscavenger.goal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.VillagePerceptionTuning;
import org.junit.jupiter.api.Test;

class VillagePerceptionEnqueueDebounceTest {

    private static final int DEBOUNCE = VillagePerceptionTuning.DEBOUNCE_TICKS;

    @Test
    void mustHappen_firstTickAtOrdinaryGameTimeIsNotBlocked() {
        VillagePerceptionEnqueueDebounce debounce = new VillagePerceptionEnqueueDebounce();
        assertFalse(debounce.shouldBlock(100_000L, DEBOUNCE));
    }

    @Test
    void mustNotHappen_legacySentinelArithmeticBlocksForever() {
        assertTrue(legacyBrokenShouldBlock(100_000L, Long.MIN_VALUE, DEBOUNCE),
                "pre-fix subtraction overflow must block the first enqueue forever");
    }

    @Test
    void mustHappen_firstEnqueueThenDebounceUntilWindowElapses() {
        VillagePerceptionEnqueueDebounce debounce = new VillagePerceptionEnqueueDebounce();
        long firstTick = 1_000L;
        debounce.recordEnqueue(firstTick);

        assertTrue(debounce.shouldBlock(firstTick + 1, DEBOUNCE), "tick + 1 inside debounce window");
        assertFalse(debounce.shouldBlock(firstTick + DEBOUNCE, DEBOUNCE), "tick + debounce eligible again");
    }

    @Test
    void mustHappen_observerAttemptsFirstEnqueueAtPositiveGameTime() {
        VillagePerceptionEnqueueDebounce debounce = new VillagePerceptionEnqueueDebounce();
        VillagePerceptionObserver observer = new VillagePerceptionObserver(null, debounce, heartbeatClock(1));

        observer.markDirty();
        assertTrue(observer.isDirty());

        boolean[] attempted = {false};
        assertTrue(observer.enqueueIfDirty(100_000L, () -> {
            attempted[0] = true;
            return true;
        }));

        assertTrue(attempted[0], "first positive gameTime must attempt observation request");
        assertFalse(observer.isDirty(), "admitted request clears dirty");
        assertTrue(debounce.shouldBlock(100_001L, DEBOUNCE), "enqueue tick recorded for debounce");
    }

    @Test
    void mustHappen_debounceBlocksImmediateRetickThenAllowsAfterWindow() {
        VillagePerceptionEnqueueDebounce debounce = new VillagePerceptionEnqueueDebounce();
        VillagePerceptionObserver observer = new VillagePerceptionObserver(null, debounce, heartbeatClock(1));

        observer.markDirty();
        assertTrue(observer.enqueueIfDirty(100L, () -> true));
        observer.markDirty();

        boolean[] secondAttempt = {false};
        assertFalse(observer.enqueueIfDirty(101L, () -> {
            secondAttempt[0] = true;
            return true;
        }));
        assertFalse(secondAttempt[0], "tick + 1 must be debounced");

        assertTrue(observer.enqueueIfDirty(120L, () -> true), "tick + 20 eligible when dirty again");
    }

    /** Pre-fix debounce: sentinel {@code Long.MIN_VALUE} made every first enqueue block forever. */
    private static boolean legacyBrokenShouldBlock(long gameTime, long lastEnqueueTick, int debounceTicks) {
        return gameTime - lastEnqueueTick < debounceTicks;
    }

    private static PhasedScanClock heartbeatClock(int entityId) {
        return new PhasedScanClock(
                entityId,
                VillagePerceptionTuning.HEARTBEAT_TICKS,
                VillagePerceptionTuning.OBSERVER_GOAL_SALT);
    }
}
