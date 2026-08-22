package com.noobk.spmscavenger.village.population;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Read-only breeder-local vacant HOME proof (G0-A-B / G0-3). Never calls {@link PoiManager#take}.
 */
public final class BreederLocalHomeProof {

    private BreederLocalHomeProof() {}

    public static boolean hasReachableVacantHome(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.isAlive()) {
            return false;
        }
        BlockPos center = villager.blockPosition();
        return level.getPoiManager()
                .getInRange(
                        holder -> holder.is(PoiTypes.HOME),
                        center,
                        PopulationFoodTuning.BREEDER_LOCAL_RADIUS,
                        PoiManager.Occupancy.HAS_SPACE)
                .anyMatch(record -> canReach(villager, record.getPos(), record.getPoiType()));
    }

    /**
     * VillagerMakeLove.canReach semantics — villager navigation, not PlayerMob.
     */
    public static boolean canReach(Villager villager, BlockPos bedPos, Holder<PoiType> poiType) {
        if (villager == null || bedPos == null || poiType == null) {
            return false;
        }
        Path path = villager.getNavigation().createPath(bedPos, poiType.value().validRange());
        return path != null && path.canReach();
    }

    /** Package-visible for tests that inject a POI stream without {@code take()}. */
    static boolean anyReachableVacantHome(
            Villager villager,
            java.util.stream.Stream<PoiRecord> vacantHomeRecords) {
        if (villager == null || vacantHomeRecords == null) {
            return false;
        }
        return vacantHomeRecords.anyMatch(record ->
                canReach(villager, record.getPos(), record.getPoiType()));
    }
}
