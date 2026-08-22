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

    private VillageWorkObservationKernel() {}

    public static Counts observe(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) {
            return incomplete();
        }
        PerceptionCoverage coverage =
                PerceptionCoverage.compute(level, anchor, VillageWorkTuning.OBSERVATION_RADIUS);
        if (!coverage.isFull()) {
            return incomplete();
        }

        int total = 0;
        int claimed = 0;
        int free = 0;
        int poiSeen = 0;

        for (PoiRecord record : level.getPoiManager()
                .getInRange(
                        holder -> holder.is(PoiTypes.HOME),
                        anchor,
                        VillageWorkTuning.OBSERVATION_RADIUS,
                        PoiManager.Occupancy.ANY)
                .toList()) {
            poiSeen++;
            if (poiSeen > VillageWorkTuning.MAX_HOME_POIS_PER_OBSERVATION) {
                return incomplete();
            }
            BlockPos pos = record.getPos();
            if (!VillagePerception.withinPerception(level, pos)
                    || !SettlementBoundsPolicy.within(pos, anchor)) {
                continue;
            }
            total++;
            if (record.isOccupied()) {
                claimed++;
            }
            if (record.hasSpace()) {
                free++;
            }
        }

        int adults = 0;
        double radius = VillageWorkTuning.OBSERVATION_RADIUS;
        AABB box = new AABB(anchor).inflate(radius, radius, radius);
        for (Villager villager : level.getEntities(EntityType.VILLAGER, box, VillageWorkObservationKernel::countsAsAdult)) {
            BlockPos feet = villager.blockPosition();
            if (!VillagePerception.withinPerception(level, feet)
                    || !SettlementBoundsPolicy.within(feet, anchor)) {
                continue;
            }
            adults++;
            if (adults > VillageWorkTuning.MAX_VILLAGERS_PER_OBSERVATION) {
                return incomplete();
            }
        }

        return new Counts(adults, total, claimed, free, WorkFactsCompleteness.COMPLETE);
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
