package com.noobk.spmscavenger.village.crop;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Per-goal transient backoff for unreachable crop targets (task-55 R1-1). */
public final class HarvestTargetBackoff {

    static final int BACKOFF_TICKS = 200;
    static final int MAX_TRACKED = 8;

    private final Map<BlockPos, Long> untilTick = new HashMap<>();

    public boolean isActive(BlockPos pos, long now) {
        Long until = untilTick.get(pos);
        return until != null && until > now;
    }

    public void recordFailure(BlockPos pos, long now) {
        prune(now);
        if (untilTick.size() >= MAX_TRACKED) {
            evictOldest();
        }
        untilTick.put(pos.immutable(), now + BACKOFF_TICKS);
    }

    void prune(long now) {
        Iterator<Map.Entry<BlockPos, Long>> iterator = untilTick.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }

    private void evictOldest() {
        BlockPos oldest = null;
        long oldestUntil = Long.MAX_VALUE;
        for (Map.Entry<BlockPos, Long> entry : untilTick.entrySet()) {
            if (entry.getValue() < oldestUntil) {
                oldestUntil = entry.getValue();
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            untilTick.remove(oldest);
        }
    }
}
