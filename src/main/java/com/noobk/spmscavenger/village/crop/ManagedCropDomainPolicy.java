package com.noobk.spmscavenger.village.crop;

import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Managed crop domain — maturity-independent (D-VR-079-A1).
 */
public final class ManagedCropDomainPolicy {

    private ManagedCropDomainPolicy() {
    }

    /**
     * @return {@code true} only when every predicate is positively established; otherwise fail open.
     */
    public static boolean isManagedCell(Mob mob, ServerLevel level, BlockPos pos) {
        if (mob == null || level == null || pos == null) {
            return false;
        }
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                level.getServer(), mob.getUUID());
        if (profile != VillageScenarioProfile.VILLAGE_ALLY) {
            return false;
        }
        BlockState cropState = level.getBlockState(pos);
        if (!CropReplantSemantics.supportedCrop(cropState)) {
            return false;
        }
        if (!CropReplantSemantics.hasValidFarmlandSupport(level, cropState, pos)) {
            return false;
        }
        VillageMemorySavedData memory = VillageMemorySavedData.peekInDimension(level);
        if (memory == null) {
            return false;
        }
        return memory.peek(mob.getUUID())
                .map(mem -> withinAnyVillage(mem, pos))
                .orElse(false);
    }

    private static boolean withinAnyVillage(MobVillageMemory memory, BlockPos pos) {
        for (KnownVillage village : memory.villages()) {
            if (SettlementBoundsPolicy.within(pos, village.anchor())) {
                return true;
            }
        }
        return false;
    }
}
