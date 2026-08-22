package com.noobk.spmscavenger.village.work;

import net.minecraft.server.MinecraftServer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Transient server-scoped cache for {@link VillageWorkFacts} (V3-D).
 *
 * <h2>Gate RET-1</h2>
 *
 * <table>
 *   <tr><th>Key</th><td>{@link SettlementIdentity}</td></tr>
 *   <tr><th>Bound</th><td>{@link VillageWorkTuning#MAX_CACHED_SETTLEMENTS}</td></tr>
 *   <tr><th>Eviction owner</th><td>{@link #put} LRU trim; {@link #invalidate}; {@link #clear}</td></tr>
 *   <tr><th>Death</th><td>N/A — server-global, not per-mob</td></tr>
 *   <tr><th>Unload</th><td>entries age to STALE via {@link FreshnessPolicy}</td></tr>
 *   <tr><th>Dimension change</th><td>partitioned by identity dimension</td></tr>
 *   <tr><th>Server stop</th><td>{@link #clear} via {@link VillageWorkFactsService#shutdown}</td></tr>
 * </table>
 */
public final class VillageWorkFactsCache {

    private static final Map<MinecraftServer, VillageWorkFactsCache> BY_SERVER = new WeakHashMap<>();

    private final Map<SettlementIdentity, VillageWorkFacts> entries = new LinkedHashMap<>();

    public static VillageWorkFactsCache forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, ignored -> new VillageWorkFactsCache());
    }

    /** Test-only isolated cache without a {@link MinecraftServer} key. */
    static VillageWorkFactsCache createForTest() {
        return new VillageWorkFactsCache();
    }

    public static void shutdown(MinecraftServer server) {
        VillageWorkFactsCache cache = BY_SERVER.remove(server);
        if (cache != null) {
            cache.clear();
        }
    }

    public Optional<VillageWorkFacts> peek(SettlementIdentity identity, long currentTick) {
        VillageWorkFacts stored = entries.get(identity);
        if (stored == null) {
            return Optional.empty();
        }
        VillageWorkFacts fresh = FreshnessPolicy.apply(stored, currentTick);
        if (fresh != stored) {
            entries.put(identity, fresh);
        }
        return Optional.of(fresh);
    }

    public void put(VillageWorkFacts facts) {
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
