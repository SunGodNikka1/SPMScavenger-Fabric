package com.noobk.spmscavenger.village.storage;

import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cheap settlement-radius diagnostic input — no {@code VillagePerception.observe()} on this path.
 */
public final class SettlementStorageFactSource {

    private SettlementStorageFactSource() {
    }

    public static SettlementStorageFact resolve(ServerLevel level, BlockPos containerPos) {
        if (level == null || containerPos == null) {
            return SettlementStorageFact.UNKNOWN;
        }
        VillageMemorySavedData data = VillageMemorySavedData.peekInDimension(level);
        if (data == null) {
            return SettlementStorageFact.UNKNOWN;
        }
        Set<BlockPos> anchors = collectAnchors(data);
        if (anchors.isEmpty()) {
            return SettlementStorageFact.UNKNOWN;
        }
        for (BlockPos anchor : anchors) {
            if (SettlementBoundsPolicy.within(containerPos, anchor)) {
                return SettlementStorageFact.IN_KNOWN_SETTLEMENT;
            }
        }
        return SettlementStorageFact.OUTSIDE_KNOWN_SETTLEMENT;
    }

    private static Set<BlockPos> collectAnchors(VillageMemorySavedData data) {
        Set<BlockPos> anchors = new HashSet<>();
        for (UUID ignored : data.trackedMobIds()) {
            data.peek(ignored).ifPresent(memory -> addAnchors(memory, anchors));
        }
        return anchors;
    }

    private static void addAnchors(MobVillageMemory memory, Set<BlockPos> anchors) {
        for (KnownVillage village : memory.villages()) {
            anchors.add(village.anchor().immutable());
        }
    }
}
