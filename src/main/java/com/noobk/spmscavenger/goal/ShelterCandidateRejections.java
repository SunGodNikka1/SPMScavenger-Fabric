package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-goal bounded negative cache preventing the same failed path probes every scan. */
final class ShelterCandidateRejections {

    static final int MAX_ENTRIES = 16;
    static final long REJECTION_TICKS = 80L;

    private final Map<BlockPos, Long> rejectedUntil = new LinkedHashMap<>();

    boolean contains(BlockPos pos) {
        return rejectedUntil.containsKey(pos);
    }

    void reject(BlockPos pos, long now) {
        sweep(now);
        rejectedUntil.put(pos.immutable(), now + REJECTION_TICKS);
        while (rejectedUntil.size() > MAX_ENTRIES) {
            Iterator<BlockPos> iterator = rejectedUntil.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    int size(long now) {
        sweep(now);
        return rejectedUntil.size();
    }

    void sweep(long now) {
        rejectedUntil.entrySet().removeIf(entry -> now > entry.getValue());
    }
}
