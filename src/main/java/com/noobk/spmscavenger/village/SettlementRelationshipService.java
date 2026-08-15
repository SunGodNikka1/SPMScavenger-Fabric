package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Single write path for mob-owned settlement relationships (D-VR-041).
 */
public final class SettlementRelationshipService {

    private SettlementRelationshipService() {
    }

    public static void onVillageRecorded(
            ServerLevel level, UUID mobId, KnownVillage village, long tick) {
        if (level == null || mobId == null || village == null) {
            return;
        }
        MobVillageMemory memory = VillageMemorySavedData.get(level).memoryOf(mobId);
        BlockPos anchor = village.anchor();
        boolean bootstrap = memory.relationshipAt(anchor).isEmpty();
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElseGet(SettlementRelationship::empty);
        if (bootstrap || tick - relationship.lastVisitTick() >= SettlementTuning.VISIT_STALE_TICKS) {
            relationship.bumpFamiliarity(SettlementTuning.VISIT_FAMILIARITY_BUMP, tick);
            memory.putRelationship(anchor, relationship);
            VillageMemorySavedData.get(level).markDirty();
        }
    }

    public static void onPresenceHeartbeat(
            ServerLevel level, UUID mobId, BlockPos mobPos, long tick) {
        if (level == null || mobId == null || mobPos == null) {
            return;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mobId);
        if (memory.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (KnownVillage village : memory.get().villages()) {
            if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
                continue;
            }
            BlockPos anchor = village.anchor();
            boolean bootstrap = memory.get().relationshipAt(anchor).isEmpty();
            SettlementRelationship relationship = memory.get().relationshipAt(anchor)
                    .orElseGet(SettlementRelationship::empty);
            if (!bootstrap && tick - relationship.lastVisitTick() < SettlementTuning.PRESENCE_HEARTBEAT_TICKS) {
                continue;
            }
            relationship.bumpFamiliarity(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, tick);
            memory.get().putRelationship(anchor, relationship);
            changed = true;
        }
        if (changed) {
            VillageMemorySavedData.get(level).markDirty();
        }
    }

    public static void onSocialEpisode(
            ServerLevel level, UUID mobId, BlockPos anchorAtStart, long tick) {
        if (level == null || mobId == null || anchorAtStart == null) {
            return;
        }
        MobVillageMemory memory = VillageMemorySavedData.get(level).memoryOf(mobId);
        SettlementRelationship relationship = memory.relationshipAt(anchorAtStart)
                .orElseGet(SettlementRelationship::empty);
        relationship.recordSocialEpisode(tick);
        memory.putRelationship(anchorAtStart, relationship);
        VillageMemorySavedData.get(level).markDirty();
    }

    public static void onHomeDesignated(ServerLevel level, UUID mobId, BlockPos anchor, long tick) {
        if (level == null || mobId == null || anchor == null) {
            return;
        }
        MobVillageMemory memory = VillageMemorySavedData.get(level).memoryOf(mobId);
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElseGet(SettlementRelationship::empty);
        relationship.applyHomeDesignationFloor();
        if (tick > relationship.lastVisitTick()) {
            relationship.bumpFamiliarity(0, tick);
        }
        memory.putRelationship(anchor, relationship);
        VillageMemorySavedData.get(level).markDirty();
    }

    /**
     * Nearest remembered village anchor within {@link SettlementBoundsPolicy}, if any.
     */
    public static Optional<BlockPos> nearestSettlementAnchorAt(
            ServerLevel level, UUID mobId, BlockPos mobPos) {
        if (level == null || mobId == null || mobPos == null) {
            return Optional.empty();
        }
        return VillageMemorySavedData.get(level).peek(mobId).flatMap(memory -> {
            BlockPos nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (KnownVillage village : memory.villages()) {
                if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
                    continue;
                }
                double dist = mobPos.distSqr(village.anchor());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = village.anchor();
                }
            }
            return Optional.ofNullable(nearest);
        });
    }
}
