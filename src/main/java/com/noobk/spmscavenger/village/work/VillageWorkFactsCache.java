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

    /** Existing server cache for passive diagnostics, or {@code null}; never creates one. */
    static VillageWorkFactsCache peekForServer(MinecraftServer server) {
        return server == null ? null : BY_SERVER.get(server);
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

    /**
     * Non-writing projection for passive diagnostics.
     *
     * <p>Freshness is evaluated at the requested tick, but the stored snapshot and access order are
     * left untouched. This is deliberately separate from the production {@link #peek} contract.
     */
    public Optional<VillageWorkFacts> peekReadOnly(SettlementIdentity identity, long currentTick) {
        VillageWorkFacts stored = entries.get(identity);
        return stored == null
                ? Optional.empty()
                : Optional.of(FreshnessPolicy.apply(stored, currentTick));
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

    VillageWorkFacts storedForTest(SettlementIdentity identity) {
        return entries.get(identity);
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
