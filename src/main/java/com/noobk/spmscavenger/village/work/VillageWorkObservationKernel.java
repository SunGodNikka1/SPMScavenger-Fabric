package com.noobk.spmscavenger.village.work;

import com.noobk.spmscavenger.village.PerceptionCoverage;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillagePerception;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pure loaded-only counting kernel for one settlement anchor (testable without scheduler).
 */
public final class VillageWorkObservationKernel {

    public record Counts(
            int adultVillagerCount,
            int totalUsableHomeCapacity,
            int claimedHomeCount,
            int currentFreeHomeCapacity,
            WorkFactsCompleteness completeness) {}

    /**
     * Lazy HOME POI enumeration with an explicit candidate budget.
     *
     * @return {@code false} when {@link VillageWorkTuning#MAX_HOME_POIS_PER_OBSERVATION} is exceeded
     */
    @FunctionalInterface
    public interface HomePoiCandidateSource {
        boolean enumerate(Consumer<PoiRecord> visitor);
    }

    /**
     * Bounded adult-villager enumeration with an explicit match budget.
     *
     * @return {@code false} when {@link VillageWorkTuning#MAX_VILLAGERS_PER_OBSERVATION} is exceeded
     */
    @FunctionalInterface
    public interface AdultVillagerCandidateSource {
        boolean enumerate(Consumer<Villager> visitor);
    }

    @FunctionalInterface
    interface SettlementEvidenceBounds {
        boolean admits(ServerLevel level, BlockPos pos, BlockPos anchor);
    }

    private static final SettlementEvidenceBounds PRODUCTION_BOUNDS = (level, pos, anchor) ->
            VillagePerception.withinPerception(level, pos) && SettlementBoundsPolicy.within(pos, anchor);

    public static Counts observe(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) {
            return incomplete();
        }
        return observe(
                level,
                anchor,
                homePoiSource(level, anchor),
                adultVillagerSource(level, anchor));
    }

    static Counts observe(
            ServerLevel level,
            BlockPos anchor,
            HomePoiCandidateSource homePois,
            AdultVillagerCandidateSource villagers) {
        if (level == null || anchor == null || homePois == null || villagers == null) {
            return incomplete();
        }
        PerceptionCoverage coverage =
                PerceptionCoverage.compute(level, anchor, VillageWorkTuning.OBSERVATION_RADIUS);
        if (!coverage.isFull()) {
            return incomplete();
        }
        return countSettlementEvidence(level, anchor, homePois, villagers, PRODUCTION_BOUNDS);
    }

    static Counts countSettlementEvidence(
            ServerLevel level,
            BlockPos anchor,
            HomePoiCandidateSource homePois,
            AdultVillagerCandidateSource villagers) {
        return countSettlementEvidence(level, anchor, homePois, villagers, PRODUCTION_BOUNDS);
    }

    static Counts countSettlementEvidence(
            ServerLevel level,
            BlockPos anchor,
            HomePoiCandidateSource homePois,
            AdultVillagerCandidateSource villagers,
            SettlementEvidenceBounds bounds) {
        if (anchor == null || homePois == null || villagers == null || bounds == null) {
            return incomplete();
        }
        int[] total = {0};
        int[] claimed = {0};
        int[] free = {0};
        boolean poisWithinBudget = homePois.enumerate(record -> {
            BlockPos pos = record.getPos();
            if (!bounds.admits(level, pos, anchor)) {
                return;
            }
            total[0]++;
            if (record.isOccupied()) {
                claimed[0]++;
            }
            if (record.hasSpace()) {
                free[0]++;
            }
        });
        if (!poisWithinBudget) {
            return incomplete();
        }

        int[] adults = {0};
        boolean villagersWithinBudget = villagers.enumerate(villager -> adults[0]++);
        if (!villagersWithinBudget) {
            return incomplete();
        }

        return new Counts(adults[0], total[0], claimed[0], free[0], WorkFactsCompleteness.COMPLETE);
    }

    private VillageWorkObservationKernel() {}

    static HomePoiCandidateSource homePoiSource(ServerLevel level, BlockPos anchor) {
        return visitor -> {
            int examined = 0;
            Iterator<PoiRecord> iterator = level.getPoiManager()
                    .getInRange(
                            holder -> holder.is(PoiTypes.HOME),
                            anchor,
                            VillageWorkTuning.OBSERVATION_RADIUS,
                            PoiManager.Occupancy.ANY)
                    .iterator();
            while (iterator.hasNext()) {
                examined++;
                if (examined > VillageWorkTuning.MAX_HOME_POIS_PER_OBSERVATION) {
                    return false;
                }
                visitor.accept(iterator.next());
            }
            return true;
        };
    }

    /**
     * Uses {@link ServerLevel#getEntities(EntityTypeTest, AABB, Predicate, List, int)} with
     * {@code maxResults = MAX + 1} so matching enumeration aborts without collecting every villager.
     */
    static AdultVillagerCandidateSource adultVillagerSource(ServerLevel level, BlockPos anchor) {
        return visitor -> {
            double radius = VillageWorkTuning.OBSERVATION_RADIUS;
            AABB box = new AABB(anchor).inflate(radius, radius, radius);
            List<Villager> matches = new ArrayList<>();
            level.getEntities(
                    EntityType.VILLAGER,
                    box,
                    villager -> countsAsAdult(villager)
                            && VillagePerception.withinPerception(level, villager.blockPosition())
                            && SettlementBoundsPolicy.within(villager.blockPosition(), anchor),
                    matches,
                    VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION + 1);
            if (matches.size() > VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION) {
                return false;
            }
            for (Villager villager : matches) {
                visitor.accept(villager);
            }
            return true;
        };
    }

    private static boolean countsAsAdult(Villager villager) {
        return villager != null
                && villager.isAlive()
                && !villager.isRemoved()
                && villager.getAge() == 0;
    }

    private static Counts incomplete() {
        return new Counts(0, 0, 0, 0, WorkFactsCompleteness.INCOMPLETE);
    }
}
