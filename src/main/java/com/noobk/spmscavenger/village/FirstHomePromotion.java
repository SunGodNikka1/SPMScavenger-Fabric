package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;

/** D-VR-042-A1 — one real, familiar settlement sleep may designate the first home. */
public final class FirstHomePromotion {

    private FirstHomePromotion() {
    }

    /**
     * Production sleep-event boundary. Reads only already-existing memory and delegates the sole
     * write to {@link VillageMemorySavedData#designateHome(ServerLevel, java.util.UUID, BlockPos,
     * long)}.
     */
    public static boolean afterSuccessfulSleep(ServerLevel level, Mob mob, BlockPos bedPos) {
        if (level == null || mob == null || bedPos == null || !mob.isSleeping()) {
            return false;
        }
        VillageMemorySavedData savedData = VillageMemorySavedData.peekInDimension(level);
        if (savedData == null) {
            return false;
        }
        Optional<MobVillageMemory> memory = savedData.peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        return promote(true, bedPos, memory.get(),
                anchor -> savedData.designateHome(level, mob.getUUID(), anchor, now));
    }

    /** Pure eligibility: no allocation, perception, nearest fallback, mutation, or durable latch. */
    static Optional<BlockPos> eligibleAnchor(
            boolean sleeping, BlockPos bedPos, MobVillageMemory memory) {
        if (!sleeping || bedPos == null || memory == null || memory.homeAnchor().isPresent()) {
            return Optional.empty();
        }

        BlockPos associated = null;
        for (KnownVillage village : memory.villages()) {
            if (!SettlementBoundsPolicy.within(bedPos, village.anchor())) {
                continue;
            }
            if (associated != null) {
                return Optional.empty();
            }
            associated = village.anchor();
        }
        if (associated == null) {
            return Optional.empty();
        }
        int familiarity = memory.relationshipAt(associated)
                .map(SettlementRelationship::familiarityScore)
                .orElse(0);
        return familiarity >= SettlementTuning.HIGH_BAND_MIN
                ? Optional.of(associated)
                : Optional.empty();
    }

    /** Testable one-shot application seam; the production writer remains the existing SavedData API. */
    static boolean promote(boolean sleeping, BlockPos bedPos, MobVillageMemory memory,
            HomeDesignator designator) {
        Optional<BlockPos> eligible = eligibleAnchor(sleeping, bedPos, memory);
        if (eligible.isEmpty()) {
            return false;
        }
        return Objects.requireNonNull(designator, "designator").designate(eligible.get());
    }

    @FunctionalInterface
    interface HomeDesignator {
        boolean designate(BlockPos anchor);
    }
}
