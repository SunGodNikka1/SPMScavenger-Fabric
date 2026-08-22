package com.noobk.spmscavenger.village.work;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shared-budget companion to {@link com.noobk.spmscavenger.village.VillagePerceptionScheduler} — at most
 * one pending refresh per {@link SettlementIdentity}.
 */
public final class VillageWorkFactsScheduler {

    private static final Map<MinecraftServer, VillageWorkFactsScheduler> BY_SERVER = new WeakHashMap<>();

    private final Map<ResourceKey<Level>, Deque<SettlementIdentity>> lanes = new HashMap<>();
    private final Set<PendingKey> pending = new HashSet<>();

    public static VillageWorkFactsScheduler forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, ignored -> new VillageWorkFactsScheduler());
    }

    /** Test-only isolated scheduler without a {@link MinecraftServer} key. */
    static VillageWorkFactsScheduler createForTest() {
        return new VillageWorkFactsScheduler();
    }

    public static void shutdown(MinecraftServer server) {
        BY_SERVER.remove(server);
    }

    public boolean requestRefresh(ResourceKey<Level> dimension, SettlementIdentity identity) {
        if (dimension == null || identity == null || !dimension.equals(identity.dimension())) {
            return false;
        }
        PendingKey key = new PendingKey(dimension, identity);
        if (pending.contains(key)) {
            return true;
        }
        lanes.computeIfAbsent(dimension, ignored -> new ArrayDeque<>()).addLast(identity);
        pending.add(key);
        return true;
    }

    int serviceUpTo(
            int budget,
            java.util.function.Function<ResourceKey<Level>, ServerLevel> levelLookup,
            long tick) {
        int serviced = 0;
        for (int i = 0; i < budget && !pending.isEmpty(); i++) {
            ResourceKey<Level> dimension = nextLaneWithWork();
            if (dimension == null) {
                break;
            }
            Deque<SettlementIdentity> lane = lanes.get(dimension);
            if (lane == null || lane.isEmpty()) {
                continue;
            }
            SettlementIdentity identity = lane.pollFirst();
            pending.remove(new PendingKey(dimension, identity));
            ServerLevel level = levelLookup.apply(dimension);
            if (level != null) {
                VillageWorkFactsService.refreshNow(level, identity, tick);
            }
            serviced++;
            if (lane.isEmpty()) {
                lanes.remove(dimension);
            }
        }
        return serviced;
    }

    int pendingCount() {
        return pending.size();
    }

    boolean hasPending(ResourceKey<Level> dimension, SettlementIdentity identity) {
        return pending.contains(new PendingKey(dimension, identity));
    }

    private ResourceKey<Level> nextLaneWithWork() {
        for (Map.Entry<ResourceKey<Level>, Deque<SettlementIdentity>> entry : lanes.entrySet()) {
            Deque<SettlementIdentity> lane = entry.getValue();
            if (lane != null && !lane.isEmpty()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private record PendingKey(ResourceKey<Level> dimension, SettlementIdentity identity) {}
}
