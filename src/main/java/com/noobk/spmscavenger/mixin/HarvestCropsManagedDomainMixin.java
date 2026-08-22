package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.compat.OptionalGoalMobResolver;
import com.noobk.spmscavenger.compat.OptionalHarvestCropTargetResolver;
import com.noobk.spmscavenger.village.crop.HarvestCropGuardCompatibility;
import com.noobk.spmscavenger.village.crop.HarvestCropVetoPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * D-VR-079-A1 — continuous managed-domain veto on pinned SPM {@code HarvestCropsGoal}.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.HarvestCropsGoal", remap = false)
public abstract class HarvestCropsManagedDomainMixin {

    @Inject(
            method = {"canUse", "method_6264"},
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void spmscavenger$vetoCanUse(CallbackInfoReturnable<Boolean> cir) {
        HarvestCropGuardCompatibility.observeCanUseHook();
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        Mob mob = OptionalGoalMobResolver.resolve(this, "managed crop veto");
        BlockPos target = OptionalHarvestCropTargetResolver.resolveTarget(this).orElse(null);
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
            HarvestCropGuardCompatibility.recordTargetResolutionFailed();
            return;
        }
        if (HarvestCropVetoPolicy.shouldVeto(mob, level, target)) {
            OptionalHarvestCropTargetResolver.clearTarget(this);
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = {"canContinueToUse", "method_6266"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void spmscavenger$vetoCanContinue(CallbackInfoReturnable<Boolean> cir) {
        HarvestCropGuardCompatibility.observeContinuationHook();
        Mob mob = OptionalGoalMobResolver.resolve(this, "managed crop veto");
        BlockPos target = OptionalHarvestCropTargetResolver.resolveTarget(this).orElse(null);
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
            HarvestCropGuardCompatibility.recordTargetResolutionFailed();
            return;
        }
        if (HarvestCropVetoPolicy.shouldVeto(mob, level, target)) {
            cir.setReturnValue(false);
        }
    }
}
