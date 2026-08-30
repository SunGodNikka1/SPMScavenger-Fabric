package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.goal.GatherResourcesGoal;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive observation of the real Gather admission, route derivation and lifecycle. */
@Mixin(GatherResourcesGoal.class)
public abstract class V4GatherHandoffLivenessMixin {

    @Shadow @Final private Mob mob;

    @Inject(method = "canUse", at = @At("HEAD"))
    private void spmscavenger_validation$gatherHead(CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.enterGather(mob.getUUID());
    }

    @Inject(method = "canUse", at = @At("RETURN"))
    private void spmscavenger_validation$gatherReturn(CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.exitGather(mob.getUUID(), cir.getReturnValue(), tick());
    }

    @Inject(method = "ownedMandatoryRoute", at = @At("RETURN"))
    private void spmscavenger_validation$ownedRoute(
            ScavengerConfig config, CallbackInfoReturnable<Optional<?>> cir) {
        V4TradeLivenessWitness.observeGatherMandatoryRoute(
                cir.getReturnValue().isPresent(), tick());
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void spmscavenger_validation$gatherStop(CallbackInfo ci) {
        V4TradeLivenessWitness.observeGatherStop(mob.getUUID(), tick());
    }

    private long tick() {
        return mob.level() instanceof ServerLevel level ? level.getGameTime() : -1L;
    }
}
