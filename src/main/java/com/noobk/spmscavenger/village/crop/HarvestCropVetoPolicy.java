package com.noobk.spmscavenger.village.crop;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;

/**
 * Continuous host {@code HarvestCropsGoal} veto inside the managed domain (fail open).
 */
public final class HarvestCropVetoPolicy {

    private HarvestCropVetoPolicy() {
    }

    public static boolean shouldVeto(Mob mob, ServerLevel level, BlockPos targetPos) {
        return ManagedCropDomainPolicy.isManagedCell(mob, level, targetPos);
    }
}
