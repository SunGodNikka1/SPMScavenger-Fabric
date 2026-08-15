package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server singleton that schedules bounded village perception under one global POI-query budget
 * (D-VR-033 / V1-D).
 *
 * <p>Queue entries are keyed by {@code (dimension, mob UUID)} with no entity references. At most one
 * pending request per key; lanes are drained round-robin across dimensions.
 */
public final class VillagePerceptionScheduler {

    private static final Map<MinecraftServer, VillagePerceptionScheduler> BY_SERVER = new WeakHashMap<>();

    private final ObservationConsumer consumer;
    private final Set<UUID> observers = new HashSet<>();
    private final Map<ResourceKey<Level>, Deque<UUID>> lanes = new HashMap<>();
    private final Set<PendingKey> pending = new HashSet<>();
    private final List<ResourceKey<Level>> laneOrder = new ArrayList<>();
    private int laneCursor = 0;
    private int admissionCursor = 0;
    private boolean warnedEmergency;

    VillagePerceptionScheduler(ObservationConsumer consumer) {
        this.consumer = consumer;
    }

    public static VillagePerceptionScheduler forServer(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(
                server,
                ignored -> new VillagePerceptionScheduler(
                        (dimension, level, mobId) -> {
                            if (level != null) {
                                VillagePerceptionService.observeAndRecord(level, mobId);
                            }
                        }));
    }

    public static void shutdown(MinecraftServer server) {
        BY_SERVER.remove(server);
    }

    /** Test-only factory with a custom consumer and emergency cap override. */
    static VillagePerceptionScheduler createForTest(ObservationConsumer consumer, int emergencyCap) {
        VillagePerceptionScheduler scheduler = new VillagePerceptionScheduler(consumer);
        scheduler.emergencyCapOverride = emergencyCap;
        return scheduler;
    }

    private int emergencyCapOverride = VillagePerceptionTuning.MAX_EMERGENCY_PENDING;

    public boolean registerObserver(UUID mobId) {
        return observers.add(mobId);
    }

    public void unregisterObserver(UUID mobId) {
        observers.remove(mobId);
        removePendingFor(mobId);
    }

    /**
     * Enqueue a deduplicated observation request.
     *
     * @return {@code true} when the mob is already pending or was admitted; {@code false} when the
     *     emergency cap refuses admission (mob must retain its dirty marker and retry)
     */
    public boolean requestObservation(ServerLevel level, UUID mobId) {
        return requestObservation(level.dimension(), mobId);
    }

    boolean requestObservation(ResourceKey<Level> dimension, UUID mobId) {
        PendingKey key = new PendingKey(dimension, mobId);
        if (pending.contains(key)) {
            return true;
        }
        int normalCapacity = Math.max(1, observers.size());
        int emergencyCap = emergencyCapOverride;
        if (pending.size() >= normalCapacity && pending.size() >= emergencyCap) {
            return false;
        }
        if (pending.size() >= normalCapacity && !warnedEmergency) {
            warnedEmergency = true;
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] Village perception queue exceeded ticking-observer bound "
                            + "(pending={}, observers={}); emergency cap {} active",
                    pending.size(),
                    observers.size(),
                    emergencyCap);
        }
        insertFair(dimension, mobId);
        pending.add(key);
        ensureLaneOrder(dimension);
        return true;
    }

    public void onServerTick(MinecraftServer server) {
        serviceUpTo(VillagePerceptionTuning.GLOBAL_QUERY_BUDGET_PER_TICK, server::getLevel);
    }

    /** Test hook — drains pending work without a full {@link MinecraftServer}. */
    int serviceUpToForTest(
            int budget, java.util.function.Function<ResourceKey<Level>, ServerLevel> levelLookup) {
        return serviceUpTo(budget, levelLookup);
    }

    private int serviceUpTo(
            int budget, java.util.function.Function<ResourceKey<Level>, ServerLevel> levelLookup) {
        int serviced = 0;
        for (int i = 0; i < budget; i++) {
            if (pending.isEmpty()) {
                break;
            }
            ResourceKey<Level> dimension = nextLaneWithWork();
            if (dimension == null) {
                break;
            }
            Deque<UUID> lane = lanes.get(dimension);
            if (lane == null || lane.isEmpty()) {
                continue;
            }
            UUID mobId = lane.pollFirst();
            pending.remove(new PendingKey(dimension, mobId));
            ServerLevel level = levelLookup.apply(dimension);
            consumer.observe(dimension, level, mobId);
            serviced++;
            if (lane.isEmpty()) {
                lanes.remove(dimension);
                laneOrder.remove(dimension);
                laneCursor = 0;
            }
        }
        return serviced;
    }

    int pendingCount() {
        return pending.size();
    }

    int registeredObserverCount() {
        return observers.size();
    }

    private void insertFair(ResourceKey<Level> dimension, UUID mobId) {
        Deque<UUID> lane = lanes.computeIfAbsent(dimension, ignored -> new ArrayDeque<>());
        if (lane.isEmpty()) {
            lane.addLast(mobId);
            return;
        }
        int size = lane.size();
        int index = Math.floorMod(admissionCursor++, size + 1);
        if (index >= size) {
            lane.addLast(mobId);
            return;
        }
        List<UUID> rotated = new ArrayList<>(lane);
        rotated.add(index, mobId);
        lane.clear();
        lane.addAll(rotated);
    }

    private ResourceKey<Level> nextLaneWithWork() {
        if (laneOrder.isEmpty()) {
            return null;
        }
        int attempts = laneOrder.size();
        for (int i = 0; i < attempts; i++) {
            if (laneCursor >= laneOrder.size()) {
                laneCursor = 0;
            }
            ResourceKey<Level> dimension = laneOrder.get(laneCursor);
            laneCursor++;
            Deque<UUID> lane = lanes.get(dimension);
            if (lane != null && !lane.isEmpty()) {
                return dimension;
            }
        }
        return null;
    }

    private void ensureLaneOrder(ResourceKey<Level> dimension) {
        if (!laneOrder.contains(dimension)) {
            laneOrder.add(dimension);
        }
    }

    private void removePendingFor(UUID mobId) {
        for (Map.Entry<ResourceKey<Level>, Deque<UUID>> entry : lanes.entrySet()) {
            entry.getValue().removeIf(id -> id.equals(mobId));
            if (entry.getValue().isEmpty()) {
                lanes.remove(entry.getKey());
                laneOrder.remove(entry.getKey());
            }
        }
        pending.removeIf(key -> key.mobId().equals(mobId));
        laneCursor = 0;
    }

    private record PendingKey(ResourceKey<Level> dimension, UUID mobId) {}

    interface ObservationConsumer {
        void observe(ResourceKey<Level> dimension, ServerLevel level, UUID mobId);
    }
}
