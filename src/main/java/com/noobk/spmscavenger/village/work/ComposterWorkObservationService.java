package com.noobk.spmscavenger.village.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * One bounded composter observation for a settlement identity — loaded chunks only.
 */
public final class ComposterWorkObservationService {

    private ComposterWorkObservationService() {}

    public static ComposterWorkFacts observe(ServerLevel level, SettlementIdentity identity, long tick) {
        if (level == null || identity == null) {
            return incomplete(identity, tick);
        }
        if (!level.dimension().equals(identity.dimension())) {
            return incomplete(identity, tick);
        }
        ComposterWorkObservationKernel.Result result =
                ComposterWorkObservationKernel.observe(level, identity.anchor());
        WorkFactsFreshness freshness = result.completeness() == WorkFactsCompleteness.COMPLETE
                ? WorkFactsFreshness.FRESH
                : WorkFactsFreshness.STALE;
        return new ComposterWorkFacts(
                identity,
                result.composterPositions(),
                tick,
                result.completeness(),
                freshness);
    }

    private static ComposterWorkFacts incomplete(SettlementIdentity identity, long tick) {
        SettlementIdentity key = identity == null
                ? SettlementIdentity.of(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO)
                : identity;
        return new ComposterWorkFacts(
                key, java.util.List.of(), tick, WorkFactsCompleteness.INCOMPLETE, WorkFactsFreshness.STALE);
    }
}
