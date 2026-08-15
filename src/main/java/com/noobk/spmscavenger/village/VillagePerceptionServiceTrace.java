package com.noobk.spmscavenger.village;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * VR-T1 driver diagnostic — last {@link VillagePerceptionService} outcome per mob, written when the
 * scheduler services a request. Not persisted; not used for gameplay.
 */
public final class VillagePerceptionServiceTrace {

    public enum RecordResult {
        /** Scheduler has not serviced this mob yet. */
        NOT_RUN,
        /** Serviced but disabled, entity missing, or not a PlayerMob before observe. */
        SKIPPED,
        /** Observed; no settlement was written (empty observation). */
        EMPTY,
        /** Observed and a {@link KnownVillage} was recorded. */
        RECORDED
    }

    public record Snapshot(
            long serviceTick,
            boolean entityResolved,
            boolean playerMobRecognized,
            int observedPois,
            RecordResult recordResult) {}

    private record Key(ResourceKey<Level> dimension, UUID mobId) {}

    private final Map<Key, Snapshot> lastByMob = new HashMap<>();
    private long lastGlobalServiceTick = -1L;

    void record(
            ResourceKey<Level> dimension,
            UUID mobId,
            long serviceTick,
            boolean entityResolved,
            boolean playerMobRecognized,
            int observedPois,
            RecordResult recordResult) {
        lastGlobalServiceTick = serviceTick;
        lastByMob.put(
                new Key(dimension, mobId),
                new Snapshot(
                        serviceTick,
                        entityResolved,
                        playerMobRecognized,
                        observedPois,
                        recordResult));
    }

    public long lastGlobalServiceTick() {
        return lastGlobalServiceTick;
    }

    public Optional<Snapshot> lastFor(ResourceKey<Level> dimension, UUID mobId) {
        return Optional.ofNullable(lastByMob.get(new Key(dimension, mobId)));
    }

    void clearMob(UUID mobId) {
        lastByMob.keySet().removeIf(key -> key.mobId().equals(mobId));
    }

    void clear() {
        lastByMob.clear();
        lastGlobalServiceTick = -1L;
    }
}
