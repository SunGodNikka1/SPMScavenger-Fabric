package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the backpack result already requested by the real trade admission invocation. */
@Mixin(PlayerMobs.class)
public abstract class V4BackpackLivenessMixin {

    @Inject(method = "backpack", at = @At("RETURN"))
    private static void spmscavenger_validation$backpack(
            Mob mob, CallbackInfoReturnable<Container> cir) {
        long tick = mob == null ? -1L : mob.level().getGameTime();
        V4TradeLivenessWitness.observeBackpackResult(cir.getReturnValue(), tick);
    }
}
