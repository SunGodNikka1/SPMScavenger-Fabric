package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.village.AttachmentBand;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.SettlementTuning;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Bounded discretionary SOCIAL utility bump near familiar settlements (D-VR-045).
 */
public final class SettlementSocialBias {

    private SettlementSocialBias() {
    }

    public static float socialBias(ServerLevel level, UUID mobId, BlockPos mobPos) {
        if (level == null || mobId == null || mobPos == null) {
            return 0f;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mobId);
        if (memory.isEmpty()) {
            return 0f;
        }
        AttachmentBand best = AttachmentBand.LOW;
        for (var village : memory.get().villages()) {
            if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
                continue;
            }
            AttachmentBand band = memory.get()
                    .relationshipAt(village.anchor())
                    .map(SettlementRelationship::attachmentBand)
                    .orElse(AttachmentBand.LOW);
            if (band.ordinal() > best.ordinal()) {
                best = band;
            }
        }
        return switch (best) {
            case HIGH -> SettlementTuning.SETTLEMENT_SOCIAL_BIAS_HIGH;
            case MEDIUM -> SettlementTuning.SETTLEMENT_SOCIAL_BIAS_MEDIUM;
            case LOW -> 0f;
        };
    }
}
