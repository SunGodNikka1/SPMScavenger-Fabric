package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class VillageWorkFactsSchedulerTest {

    @Test
    void mustHappen_deduplicatesPendingSettlementRefresh() {
        VillageWorkFactsScheduler scheduler = VillageWorkFactsScheduler.createForTest();
        SettlementIdentity identity =
                SettlementIdentity.of(Level.OVERWORLD, new BlockPos(8, 64, 8));
        assertTrue(scheduler.requestRefresh(Level.OVERWORLD, identity));
        assertTrue(scheduler.requestRefresh(Level.OVERWORLD, identity));
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    void mustHappen_sharesBudgetWithPerceptionScheduler() {
        VillageWorkFactsScheduler scheduler = VillageWorkFactsScheduler.createForTest();
        for (int i = 0; i < 4; i++) {
            scheduler.requestRefresh(
                    Level.OVERWORLD, SettlementIdentity.of(Level.OVERWORLD, new BlockPos(i, 64, i)));
        }
        int serviced = scheduler.serviceUpTo(2, dimension -> null, 0L, (level, identity, tick) -> {});
        assertEquals(2, serviced);
        assertEquals(2, scheduler.pendingCount());
    }

    @Test
    void close56_1_anchorSupersedeCancelsStalePendingIdentity() {
        VillageWorkFactsScheduler scheduler = VillageWorkFactsScheduler.createForTest();
        VillageWorkFactsCache cache = VillageWorkFactsCache.createForTest();
        SettlementIdentity a = SettlementIdentity.of(Level.OVERWORLD, new BlockPos(1, 64, 1));
        SettlementIdentity b = SettlementIdentity.of(Level.OVERWORLD, new BlockPos(2, 64, 2));

        scheduler.requestRefresh(Level.OVERWORLD, a);
        cache.put(sample(a, 5L));
        cache.invalidate(a);
        scheduler.cancelPending(Level.OVERWORLD, a);
        scheduler.requestRefresh(Level.OVERWORLD, b);

        Map<SettlementIdentity, Integer> refreshCounts = new HashMap<>();
        scheduler.serviceUpTo(
                4,
                dimension -> null,
                10L,
                (level, identity, tick) -> refreshCounts.merge(identity, 1, Integer::sum));

        assertEquals(0, refreshCounts.getOrDefault(a, 0), "stale A must refresh 0 times");
        assertEquals(1, refreshCounts.getOrDefault(b, 0), "canonical B must refresh once");
        assertFalse(cache.peek(a, 10L).isPresent(), "A must not repopulate cache");
        assertFalse(scheduler.hasPending(Level.OVERWORLD, a));
        assertFalse(scheduler.hasPending(Level.OVERWORLD, b));
    }

    private static VillageWorkFacts sample(SettlementIdentity identity, long tick) {
        return new VillageWorkFacts(
                identity, 2, 3, 1, 2, tick, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH);
    }
}
