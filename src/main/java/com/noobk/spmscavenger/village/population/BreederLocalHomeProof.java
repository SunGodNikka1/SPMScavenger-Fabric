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
import java.util.function.Predicate;

/**
 * Read-only breeder-local vacant HOME proof (G0-A-B / G0-3). Never calls {@link PoiManager#take}.
 */
public final class BreederLocalHomeProof {

    /**
     * Lazy vacant-HOME enumeration for injectable budget tests.
     *
     * <p>{@code false} from the visitor stops the provider immediately.
     */
    @FunctionalInterface
    public interface VacantHomeCandidateSource {
        void enumerate(Predicate<PoiRecord> visitor);
    }

    private BreederLocalHomeProof() {}

    public static boolean hasReachableVacantHome(ServerLevel level, Villager villager) {
        if (level == null || villager == null || !villager.isAlive()) {
            return false;
        }
        BlockPos center = villager.blockPosition();
        Iterator<PoiRecord> iterator = level.getPoiManager()
                .getInRange(
                        holder -> holder.is(PoiTypes.HOME),
                        center,
                        PopulationFoodTuning.BREEDER_LOCAL_RADIUS,
                        PoiManager.Occupancy.HAS_SPACE)
                .iterator();
        int probes = 0;
        while (iterator.hasNext()) {
            if (probes >= PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                break;
            }
            PoiRecord record = iterator.next();
            probes++;
            if (canReach(villager, record.getPos(), record.getPoiType())) {
                return true;
            }
        }
        return false;
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

    /**
     * Test seam — existential proof with bounded provider + probe work.
     */
    static boolean anyReachableVacantHome(
            VacantHomeCandidateSource vacantHomes,
            java.util.function.IntPredicate reachableOnProbeIndex) {
        if (vacantHomes == null || reachableOnProbeIndex == null) {
            return false;
        }
        int[] probes = {0};
        boolean[] found = {false};
        vacantHomes.enumerate(record -> {
            if (found[0]) {
                return false;
            }
            if (probes[0] >= PopulationFoodTuning.MAX_HOME_PROBES_PER_RECIPIENT) {
                return false;
            }
            probes[0]++;
            if (reachableOnProbeIndex.test(probes[0])) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }
}
