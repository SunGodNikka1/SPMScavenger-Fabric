package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * Package-private service boundary for V1-D: one POI touch at {@link VillagePerception#observe}
 * and one memory write at {@link VillageMemorySavedData#record}.
 */
final class VillagePerceptionService {

    static Optional<KnownVillage> observeAndRecord(ServerLevel level, UUID mobId) {
        long tick = level.getGameTime();
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.forServer(level.getServer());
        if (!ScavengerConfig.get().enabled) {
            scheduler.recordServiceTrace(
                    level, mobId, tick, false, false, 0, VillagePerceptionServiceTrace.RecordResult.SKIPPED);
            return Optional.empty();
        }
        Entity entity = level.getEntity(mobId);
        if (entity == null) {
            scheduler.recordServiceTrace(
                    level, mobId, tick, false, false, 0, VillagePerceptionServiceTrace.RecordResult.SKIPPED);
            return Optional.empty();
        }
        if (!(entity instanceof Mob mob)) {
            scheduler.recordServiceTrace(
                    level, mobId, tick, true, false, 0, VillagePerceptionServiceTrace.RecordResult.SKIPPED);
            return Optional.empty();
        }
        if (!PlayerMobs.isPlayerMob(mob)) {
            scheduler.recordServiceTrace(
                    level, mobId, tick, true, false, 0, VillagePerceptionServiceTrace.RecordResult.SKIPPED);
            return Optional.empty();
        }
        VillagePerception.Observation observation =
                VillagePerception.observe(level, mob.blockPosition());
        Optional<KnownVillage> recorded =
                VillageMemorySavedData.get(level).record(mobId, observation, tick);
        VillagePerceptionServiceTrace.RecordResult result = recorded.isPresent()
                ? VillagePerceptionServiceTrace.RecordResult.RECORDED
                : VillagePerceptionServiceTrace.RecordResult.EMPTY;
        scheduler.recordServiceTrace(
                level,
                mobId,
                tick,
                true,
                true,
                observation.admittedPoiCount(),
                result);
        return recorded;
    }

    private VillagePerceptionService() {}
}
