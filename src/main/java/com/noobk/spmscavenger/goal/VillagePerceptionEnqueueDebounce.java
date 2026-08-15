package com.noobk.spmscavenger.goal;

/**
 * Per-observer enqueue spacing for V1-D. "Never enqueued" is explicit state — not a fake timestamp —
 * so the first request at ordinary positive {@code gameTime} cannot trip signed-overflow debounce.
 */
final class VillagePerceptionEnqueueDebounce {

    private boolean hasEnqueued;
    private long lastEnqueueTick;

    boolean shouldBlock(long gameTime, int debounceTicks) {
        return hasEnqueued && gameTime - lastEnqueueTick < debounceTicks;
    }

    void recordEnqueue(long gameTime) {
        hasEnqueued = true;
        lastEnqueueTick = gameTime;
    }
}
