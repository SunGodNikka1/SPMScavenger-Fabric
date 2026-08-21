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

import java.util.ArrayList;
import java.util.List;

/**
 * One read-only snapshot of ally profile + remembered village anchors for a bounded crop scan.
 */
public final class ManagedCropDomainContext {

    private final boolean ally;
    private final List<BlockPos> villageAnchors;

    private ManagedCropDomainContext(boolean ally, List<BlockPos> villageAnchors) {
        this.ally = ally;
        this.villageAnchors = List.copyOf(villageAnchors);
    }

    /** Test seam — production uses {@link #capture}. */
    static ManagedCropDomainContext forTests(boolean ally, List<BlockPos> anchors) {
        return new ManagedCropDomainContext(ally, anchors);
    }

    public static ManagedCropDomainContext capture(Mob mob, ServerLevel level) {
        if (mob == null || level == null) {
            return new ManagedCropDomainContext(false, List.of());
        }
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                level.getServer(), mob.getUUID());
        if (profile != VillageScenarioProfile.VILLAGE_ALLY) {
            return new ManagedCropDomainContext(false, List.of());
        }
        VillageMemorySavedData memory = VillageMemorySavedData.peekInDimension(level);
        if (memory == null) {
            return new ManagedCropDomainContext(true, List.of());
        }
        List<BlockPos> anchors = new ArrayList<>();
        memory.peek(mob.getUUID()).ifPresent(mem -> collectAnchors(mem, anchors));
        return new ManagedCropDomainContext(true, anchors);
    }

    public boolean isManagedCell(CropWorldView world, BlockPos pos, BlockState cropState) {
        if (!ally || world == null || pos == null) {
            return false;
        }
        if (!CropReplantSemantics.supportedCrop(cropState)) {
            return false;
        }
        if (!(world.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.FarmBlock)) {
            return false;
        }
        if (villageAnchors.isEmpty()) {
            return false;
        }
        for (BlockPos anchor : villageAnchors) {
            if (SettlementBoundsPolicy.within(pos, anchor)) {
                return true;
            }
        }
        return false;
    }

    private static void collectAnchors(MobVillageMemory memory, List<BlockPos> anchors) {
        for (KnownVillage village : memory.villages()) {
            anchors.add(village.anchor().immutable());
        }
    }
}
