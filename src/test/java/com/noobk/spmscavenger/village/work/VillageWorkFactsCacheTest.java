package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class VillageWorkFactsCacheTest {

    @Test
    void mustHappen_anchorSupersedeInvalidatesStaleIdentity() {
        VillageWorkFactsDiagnostics.resetForTest();
        VillageWorkFactsCache cache = VillageWorkFactsCache.createForTest();
        SettlementIdentity oldId = SettlementIdentity.of(Level.OVERWORLD, new BlockPos(1, 64, 1));
        cache.put(sample(oldId, 10L));
        assertTrue(cache.peek(oldId, 10L).isPresent());

        cache.invalidate(oldId);
        assertFalse(cache.peek(oldId, 10L).isPresent());
    }

    @Test
    void mustHappen_boundedEvictionDropsOldestEntry() {
        VillageWorkFactsCache cache = VillageWorkFactsCache.createForTest();
        for (int i = 0; i < VillageWorkTuning.MAX_CACHED_SETTLEMENTS + 3; i++) {
            cache.put(sample(
                    SettlementIdentity.of(Level.OVERWORLD, new BlockPos(i, 64, 0)), i));
        }
        assertEquals(VillageWorkTuning.MAX_CACHED_SETTLEMENTS, cache.size());
    }

    private static VillageWorkFacts sample(SettlementIdentity identity, long tick) {
        return new VillageWorkFacts(
                identity, 2, 3, 1, 2, tick, WorkFactsCompleteness.COMPLETE, WorkFactsFreshness.FRESH);
    }
}
