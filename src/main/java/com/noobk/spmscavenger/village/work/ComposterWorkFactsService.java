package com.noobk.spmscavenger.village.work;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API for V3-F composter work facts — shares scheduler cadence with task-56.
 */
public final class ComposterWorkFactsService {

    private ComposterWorkFactsService() {}

    public static void shutdown(MinecraftServer server) {
        ComposterWorkFactsCache.shutdown(server);
    }

    public static void invalidate(ServerLevel level, SettlementIdentity identity) {
        if (level == null || identity == null) {
            return;
        }
        ComposterWorkFactsCache.forServer(level.getServer()).invalidate(identity);
    }

    public static void scheduleForMob(ServerLevel level, UUID mobId) {
        VillageWorkFactsService.scheduleForMob(level, mobId);
    }

    public static void refreshNow(ServerLevel level, SettlementIdentity identity, long tick) {
        if (level == null || identity == null) {
            return;
        }
        ComposterWorkFacts facts = ComposterWorkObservationService.observe(level, identity, tick);
        ComposterWorkFactsCache.forServer(level.getServer()).put(facts);
    }

    public static Optional<ComposterWorkFacts> peek(ServerLevel level, SettlementIdentity identity) {
        if (level == null || identity == null) {
            return Optional.empty();
        }
        return ComposterWorkFactsCache.forServer(level.getServer())
                .peek(identity, level.getGameTime());
    }
}
