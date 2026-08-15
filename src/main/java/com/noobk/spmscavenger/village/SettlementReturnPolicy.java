package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * When a mob with home or high familiarity should path back toward a settlement (V1.5-C).
 */
public final class SettlementReturnPolicy {

    private SettlementReturnPolicy() {
    }

    public static boolean shouldCommute(ServerLevel level, Mob mob) {
        if (level == null || mob == null || !ScavengerConfig.get().exploring) {
            return false;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        Optional<BlockPos> target = commuteTarget(memory.get(), mob.blockPosition());
        if (target.isEmpty()) {
            return false;
        }
        BlockPos anchor = target.get();
        if (SettlementBoundsPolicy.within(mob.blockPosition(), anchor)) {
            return false;
        }
        double dist = Math.sqrt(mob.blockPosition().distSqr(anchor));
        return dist >= SettlementTuning.COMMUTE_MIN_DISTANCE;
    }

    public static Optional<BlockPos> commuteTarget(MobVillageMemory memory, BlockPos mobPos) {
        if (memory == null) {
            return Optional.empty();
        }
        Optional<KnownVillage> home = memory.home();
        if (home.isPresent()) {
            return Optional.of(home.get().anchor());
        }
        return memory.villages().stream()
                .filter(village -> memory.relationshipAt(village.anchor())
                        .map(rel -> rel.attachmentBand() == AttachmentBand.HIGH)
                        .orElse(false))
                .min(Comparator.comparingDouble(v -> mobPos.distSqr(v.anchor())))
                .map(KnownVillage::anchor);
    }

    public static Optional<BlockPos> commuteTarget(ServerLevel level, Mob mob) {
        return VillageMemorySavedData.get(level).peek(mob.getUUID())
                .flatMap(memory -> commuteTarget(memory, mob.blockPosition()));
    }

    public static boolean qualifiesForCommute(MobVillageMemory memory, BlockPos anchor) {
        if (memory == null || anchor == null) {
            return false;
        }
        return memory.home()
                .filter(v -> VillageIdentityPolicy.sameSettlement(v.anchor(), anchor))
                .isPresent()
                || memory.relationshipAt(anchor)
                        .map(rel -> rel.attachmentBand() == AttachmentBand.HIGH)
                        .orElse(false);
    }
}
