package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.work.VillageWorkFactsService;
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
        if (!ScavengerConfig.get().enabled) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(mobId);
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            return Optional.empty();
        }
        VillagePerception.Observation observation =
                VillagePerception.observe(level, mob.blockPosition());
        Optional<KnownVillage> remembered = VillageMemorySavedData.get(level).record(
                level, mobId, observation, tick, mob.blockPosition());
        remembered.ifPresent(ignored -> VillageWorkFactsService.scheduleForMob(level, mobId));
        return remembered;
    }

    private VillagePerceptionService() {}
}
