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
            ServerLevel level, UUID mobId, KnownVillage village, long tick, BlockPos mobPos) {
        if (level == null || mobId == null || village == null || mobPos == null) {
            return;
        }
        if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
            return;
        }
        MobVillageMemory memory = VillageMemorySavedData.get(level).memoryOf(mobId);
        BlockPos anchor = village.anchor();
        boolean bootstrap = memory.relationshipAt(anchor).isEmpty();
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElseGet(SettlementRelationship::empty);
        if (bootstrap || relationship.qualifiesForReentryVisit()) {
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
            BlockPos anchor = village.anchor();
            SettlementRelationship relationship = memory.get().relationshipAt(anchor)
                    .orElseGet(SettlementRelationship::empty);
            if (SettlementBoundsPolicy.within(mobPos, anchor)) {
                boolean bootstrap = memory.get().relationshipAt(anchor).isEmpty();
                if (!bootstrap && tick - relationship.lastPresenceTick()
                        < SettlementTuning.PRESENCE_HEARTBEAT_TICKS) {
                    continue;
                }
                relationship.recordPresenceHeartbeat(SettlementTuning.PRESENCE_FAMILIARITY_BUMP, tick);
            } else {
                relationship.noteOutsideBounds(tick);
            }
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

    /**
     * V2-G — credit one completed trade visit/chain to the settlement it happened in.
     *
     * <p>Deliberately <b>not</b> routed through {@link #onSocialEpisode}: `D-VR-057` separates the
     * credit so a shopping trip is never counted as a social event. The caller owns the
     * once-per-visit rule (`D-VR-063`); this method credits whatever it is handed, exactly once.
     *
     * @param anchorAtStart the settlement the episode belongs to, resolved at the <b>first
     *     successful transaction</b> of the visit — not at round or chain start, and never
     *     re-resolved at teardown. First success is the moment an episode demonstrably exists and
     *     the mob is provably standing at the villager; resolving later would credit whichever
     *     village the mob had wandered into by teardown
     */
    public static void onTradeEpisode(
            ServerLevel level, UUID mobId, BlockPos anchorAtStart, long tick) {
        if (level == null || mobId == null || anchorAtStart == null) {
            return;
        }
        MobVillageMemory memory = VillageMemorySavedData.get(level).memoryOf(mobId);
        SettlementRelationship relationship = memory.relationshipAt(anchorAtStart)
                .orElseGet(SettlementRelationship::empty);
        relationship.recordTradeEpisode(tick);
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
