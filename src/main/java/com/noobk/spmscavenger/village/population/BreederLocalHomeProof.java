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

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Read-only breeder-local vacant HOME proof (G0-A-B / G0-3). Never calls {@link PoiManager#take}.
 */
public final class BreederLocalHomeProof {

    /**
     * Lazy vacant-HOME enumeration with an explicit path-probe budget per recipient.
     *
     * @return {@code false} when {@link PopulationFoodTuning#MAX_HOME_PROBES_PER_RECIPIENT} is exceeded
     */
    @FunctionalInterface
    public interface VacantHomeCandidateSource {
        boolean enumerate(Consumer<PoiRecord> visitor);
    }

    private BreederLocalHomeProof() {}

    public static boolean hasReachableVacantHome(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.isAlive()) {
            return false;
        }
        BlockPos center = villager.blockPosition();
        return anyReachableVacantHome(villager, vacantHomeSource(level, center));
    }

    static VacantHomeCandidateSource vacantHomeSource(ServerLevel level, BlockPos center) {
        return visitor -> {
            Iterator<PoiRecord> iterator = level.getPoiManager()
                    .getInRange(
                            holder -> holder.is(PoiTypes.HOME),
                            center,
                            PopulationFoodTuning.BREEDER_LOCAL_RADIUS,
                            PoiManager.Occupancy.HAS_SPACE)
                    .iterator();
            int examined = 0;
            while (iterator.hasNext()) {
                examined++;
                if (examined > PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                    return false;
                }
                visitor.accept(iterator.next());
            }
            return true;
        };
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

    static boolean anyReachableVacantHome(
            Villager villager,
            VacantHomeCandidateSource vacantHomes) {
        if (villager == null || vacantHomes == null) {
            return false;
        }
        boolean[] found = {false};
        boolean withinBudget = vacantHomes.enumerate(record -> {
            if (!found[0] && canReach(villager, record.getPos(), record.getPoiType())) {
                found[0] = true;
            }
        });
        return withinBudget && found[0];
    }
}
