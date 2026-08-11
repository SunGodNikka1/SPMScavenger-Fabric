package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.goal.CraftTorchesGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional common-side bridge into Social Player Mobs' server-owned objective formatter. */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.ObjectiveReadout")
public abstract class ObjectiveReadoutMixin {

    /**
     * SPM remains optional. If its private formatter changes, {@code require = 0} leaves the host's
     * normal class-name fallback intact instead of making this addon a startup dependency.
     */
    @Inject(
            method = "describe",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void spmscavenger$describeCrafting(
            Goal goal, CallbackInfoReturnable<String> cir) {
        if (goal instanceof CraftTorchesGoal crafting) {
            cir.setReturnValue(crafting.craftingReadout());
        }
    }
}
