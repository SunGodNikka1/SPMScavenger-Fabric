package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

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
        int serviced = scheduler.serviceUpTo(2, dimension -> null, 0L);
        assertEquals(2, serviced);
        assertEquals(2, scheduler.pendingCount());
    }
}
