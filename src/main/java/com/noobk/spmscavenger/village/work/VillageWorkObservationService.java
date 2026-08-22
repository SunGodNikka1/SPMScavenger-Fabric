package com.noobk.spmscavenger.village.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * One bounded observation for a settlement identity — loaded chunks only.
 */
public final class VillageWorkObservationService {

    private VillageWorkObservationService() {}

    public static VillageWorkFacts observe(ServerLevel level, SettlementIdentity identity, long tick) {
        if (level == null || identity == null) {
            return incomplete(identity, tick);
        }
        if (!level.dimension().equals(identity.dimension())) {
            return incomplete(identity, tick);
        }
        VillageWorkObservationKernel.Counts counts =
                VillageWorkObservationKernel.observe(level, identity.anchor());
        WorkFactsFreshness freshness = counts.completeness() == WorkFactsCompleteness.COMPLETE
                ? WorkFactsFreshness.FRESH
                : WorkFactsFreshness.STALE;
        return new VillageWorkFacts(
                identity,
                counts.adultVillagerCount(),
                counts.totalUsableHomeCapacity(),
                counts.claimedHomeCount(),
                counts.currentFreeHomeCapacity(),
                tick,
                counts.completeness(),
                freshness);
    }

    private static VillageWorkFacts incomplete(SettlementIdentity identity, long tick) {
        SettlementIdentity key = identity == null
                ? SettlementIdentity.of(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO)
                : identity;
        return new VillageWorkFacts(
                key, 0, 0, 0, 0, tick, WorkFactsCompleteness.INCOMPLETE, WorkFactsFreshness.STALE);
    }
}
