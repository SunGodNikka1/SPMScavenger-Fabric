package com.noobk.spmscavenger.village.work;

import net.minecraft.server.MinecraftServer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Transient server-scoped cache for {@link ComposterWorkFacts} (V3-F).
 */
public final class ComposterWorkFactsCache {

    private static final Map<MinecraftServer, ComposterWorkFactsCache> BY_SERVER = new WeakHashMap<>();

    private final Map<SettlementIdentity, ComposterWorkFacts> entries = new LinkedHashMap<>();

    public static ComposterWorkFactsCache forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, ignored -> new ComposterWorkFactsCache());
    }

    static ComposterWorkFactsCache createForTest() {
        return new ComposterWorkFactsCache();
    }

    public static void shutdown(MinecraftServer server) {
        ComposterWorkFactsCache cache = BY_SERVER.remove(server);
        if (cache != null) {
            cache.clear();
        }
    }

    public Optional<ComposterWorkFacts> peek(SettlementIdentity identity, long currentTick) {
        ComposterWorkFacts stored = entries.get(identity);
        if (stored == null) {
            return Optional.empty();
        }
        ComposterWorkFacts fresh = FreshnessPolicy.apply(stored, currentTick);
        if (fresh != stored) {
            entries.put(identity, fresh);
        }
        return Optional.of(fresh);
    }

    public void put(ComposterWorkFacts facts) {
        if (facts == null) {
            return;
        }
        entries.put(facts.identity(), facts);
        trim();
    }

    public void invalidate(SettlementIdentity identity) {
        if (identity != null) {
            entries.remove(identity);
        }
    }

    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private void trim() {
        while (entries.size() > VillageWorkTuning.MAX_CACHED_SETTLEMENTS) {
            Iterator<SettlementIdentity> iterator = entries.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }
}
