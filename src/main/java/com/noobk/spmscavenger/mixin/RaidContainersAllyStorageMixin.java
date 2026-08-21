package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.compat.OptionalGoalMobResolver;
import com.noobk.spmscavenger.compat.OptionalRaidContainerTargetResolver;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import com.noobk.spmscavenger.village.storage.StorageGuardCompatibility;
import com.noobk.spmscavenger.village.storage.StorageRaidPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * D-VR-081 — continuous ally storage guard on pinned SPM {@code RaidContainersGoal}.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.RaidContainersGoal", remap = false)
public abstract class RaidContainersAllyStorageMixin {

    @Inject(
            method = {"canUse", "method_6264"},
            at = @At("RETURN"),
            cancellable = true,
            require = 0)
    private void spmscavenger$guardCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        StorageGuardCompatibility.observeCanUseHook();
        Mob mob = OptionalGoalMobResolver.resolve(this, "raid storage guard");
        if (mob != null && !isAlly(mob)) {
            return;
        }
        BlockPos target = OptionalRaidContainerTargetResolver.resolveTarget(this).orElse(null);
        if (mob == null || target == null || !(mob.level() instanceof ServerLevel level)) {
            OptionalRaidContainerTargetResolver.clearTarget(this);
            StorageGuardCompatibility.recordTargetResolutionFailed();
            cir.setReturnValue(false);
            return;
        }
        if (!StorageRaidPolicy.mayLoot(mob, level, target)) {
            OptionalRaidContainerTargetResolver.clearTarget(this);
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = {"canContinueToUse", "method_6266"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void spmscavenger$guardCanContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        StorageGuardCompatibility.observeContinuationHook();
        Mob mob = OptionalGoalMobResolver.resolve(this, "raid storage guard");
        if (mob == null || !isAlly(mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            StorageGuardCompatibility.recordTargetResolutionFailed();
            cir.setReturnValue(false);
            return;
        }
        BlockPos target = OptionalRaidContainerTargetResolver.resolveTarget(this).orElse(null);
        if (target == null) {
            StorageGuardCompatibility.recordTargetResolutionFailed();
            cir.setReturnValue(false);
            return;
        }
        if (!StorageRaidPolicy.mayLoot(mob, level, target)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isAlly(Mob mob) {
        if (mob == null) {
            return false;
        }
        return PlayerMobVillagePolicySavedData.profileOf(
                mob.level().getServer(), mob.getUUID())
                == VillageScenarioProfile.VILLAGE_ALLY;
    }
}
