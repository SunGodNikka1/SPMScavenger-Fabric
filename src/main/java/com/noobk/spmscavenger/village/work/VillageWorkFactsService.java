package com.noobk.spmscavenger.village.work;

import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates cache, scheduler, and observation for V3-D work facts.
 */
public final class VillageWorkFactsService {

    private VillageWorkFactsService() {}

    public static void shutdown(MinecraftServer server) {
        VillageWorkFactsCache.shutdown(server);
        VillageWorkFactsScheduler.shutdown(server);
        ComposterWorkFactsService.shutdown(server);
    }

    public static void onAnchorSuperseded(ServerLevel level, BlockPos oldAnchor) {
        if (level == null || oldAnchor == null) {
            return;
        }
        SettlementIdentity stale = SettlementIdentity.of(level.dimension(), oldAnchor);
        VillageWorkFactsCache.forServer(level.getServer()).invalidate(stale);
        ComposterWorkFactsService.invalidate(level, stale);
        VillageWorkFactsScheduler.forServer(level.getServer()).cancelPending(level.dimension(), stale);
        VillageWorkFactsDiagnostics.recordAnchorInvalidation();
    }

    public static void scheduleForMob(ServerLevel level, UUID mobId) {
        if (level == null || mobId == null) {
            return;
        }
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        if (data == null) {
            return;
        }
        data.peek(mobId).ifPresent(memory -> scheduleForMemory(level, memory));
    }

    public static void scheduleForMemory(ServerLevel level, MobVillageMemory memory) {
        if (level == null || memory == null) {
            return;
        }
        VillageWorkFactsScheduler scheduler = VillageWorkFactsScheduler.forServer(level.getServer());
        ResourceKey<Level> dimension = level.dimension();
        for (KnownVillage village : memory.villages()) {
            SettlementIdentity identity = SettlementIdentity.of(dimension, village.anchor());
            scheduler.requestRefresh(dimension, identity);
        }
    }

    public static void refreshNow(ServerLevel level, SettlementIdentity identity, long tick) {
        if (level == null || identity == null) {
            return;
        }
        VillageWorkFacts facts = VillageWorkObservationService.observe(level, identity, tick);
        VillageWorkFactsCache.forServer(level.getServer()).put(facts);
        ComposterWorkFactsService.refreshNow(level, identity, tick);
        if (facts.completeness() == WorkFactsCompleteness.COMPLETE) {
            VillageWorkFactsDiagnostics.recordCompleteObservation();
        } else {
            VillageWorkFactsDiagnostics.recordIncompleteObservation();
        }
    }

    public static Optional<VillageWorkFacts> peek(ServerLevel level, SettlementIdentity identity) {
        if (level == null || identity == null) {
            return Optional.empty();
        }
        return VillageWorkFactsCache.forServer(level.getServer())
                .peek(identity, level.getGameTime());
    }

    /**
     * Non-creating, non-writing read for passive runtime diagnostics.
     *
     * <p>Unlike {@link #peek}, this does not materialize a server cache and does not persist a
     * freshness transition into an existing cache.
     */
    public static Optional<VillageWorkFacts> peekReadOnly(
            ServerLevel level, SettlementIdentity identity) {
        if (level == null || identity == null) {
            return Optional.empty();
        }
        VillageWorkFactsCache cache = VillageWorkFactsCache.peekForServer(level.getServer());
        return cache == null
                ? Optional.empty()
                : cache.peekReadOnly(identity, level.getGameTime());
    }

    public static int drainBudget(MinecraftServer server, int budget) {
        if (server == null || budget <= 0) {
            return 0;
        }
        long tick = server.overworld().getGameTime();
        return VillageWorkFactsScheduler.forServer(server)
                .serviceUpTo(budget, server::getLevel, tick);
    }
}
