package com.noobk.spmscavenger.village.crop;

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
        BlockState cropState = level.getBlockState(pos);
        if (!CropReplantSemantics.supportedCrop(cropState)) {
            return false;
        }
        return ManagedCropDomainContext.capture(mob, level)
                .isManagedCell(CropWorldView.from(level), pos, cropState);
    }
}
