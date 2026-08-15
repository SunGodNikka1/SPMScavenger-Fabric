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

    /**
     * Whether to <b>start</b> a new return commute (D-VR-048). Requires {@link SettlementTuning#COMMUTE_MIN_DISTANCE}
     * so Bob does not commute when already near home.
     */
    public static boolean shouldStartCommute(ServerLevel level, Mob mob) {
        if (level == null || mob == null || !ScavengerConfig.get().exploring) {
            return false;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        Optional<BlockPos> target = commuteTarget(memory.get(), mob.blockPosition());
        return target.filter(anchor -> shouldStartCommuteAt(memory.get(), anchor, mob.blockPosition()))
                .isPresent();
    }

    /**
     * Whether to <b>start</b> a new return commute at {@code mobPos} toward {@code anchor}.
     * Package-visible for unit tests without a live level/mob.
     */
    static boolean shouldStartCommuteAt(MobVillageMemory memory, BlockPos anchor, BlockPos mobPos) {
        if (memory == null || anchor == null || mobPos == null) {
            return false;
        }
        if (!qualifiesForCommute(memory, anchor)) {
            return false;
        }
        if (SettlementBoundsPolicy.within(mobPos, anchor)) {
            return false;
        }
        double dist = Math.sqrt(mobPos.distSqr(anchor));
        return dist >= SettlementTuning.COMMUTE_MIN_DISTANCE;
    }

    /**
     * Whether an in-flight {@code COMMUTE} may chain another leg. Terminates only at
     * {@link SettlementBoundsPolicy} — no {@link SettlementTuning#COMMUTE_MIN_DISTANCE} dead zone.
     */
    public static boolean shouldContinueCommute(ServerLevel level, Mob mob, BlockPos anchor) {
        if (level == null || mob == null || anchor == null || !ScavengerConfig.get().exploring) {
            return false;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        return shouldContinueCommuteAt(memory.get(), anchor, mob.blockPosition());
    }

    /**
     * Whether an in-flight {@code COMMUTE} may chain another leg at {@code mobPos}.
     * Package-visible for unit tests without a live level/mob.
     */
    static boolean shouldContinueCommuteAt(MobVillageMemory memory, BlockPos anchor, BlockPos mobPos) {
        if (memory == null || anchor == null || mobPos == null) {
            return false;
        }
        if (!qualifiesForCommute(memory, anchor)) {
            return false;
        }
        return !SettlementBoundsPolicy.within(mobPos, anchor);
    }

    /** @deprecated use {@link #shouldStartCommute} or {@link #shouldContinueCommute} */
    @Deprecated
    public static boolean shouldCommute(ServerLevel level, Mob mob) {
        return shouldStartCommute(level, mob);
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
