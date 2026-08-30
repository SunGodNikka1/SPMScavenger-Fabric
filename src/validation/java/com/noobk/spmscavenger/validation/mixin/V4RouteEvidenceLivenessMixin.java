package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.trade.RouteExhaustionEvidence;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the exact production evidence publication/read calls and their shared identity. */
@Mixin(RouteExhaustionEvidence.class)
public abstract class V4RouteEvidenceLivenessMixin {

    @Inject(method = "publish", at = @At("RETURN"))
    private static void spmscavenger_validation$published(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand,
            RouteExhaustionEvidence.Reason reason, long gameTime, CallbackInfo ci) {
        V4TradeLivenessWitness.observeRouteEvidencePublish(mobId, demand, gameTime);
    }

    @Inject(method = "exhaustedFor", at = @At("RETURN"))
    private static void spmscavenger_validation$read(
            UUID mobId, WorkDemandPolicy.MaterialDemand demand, long gameTime,
            CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.observeRouteEvidenceRead(
                mobId, demand, cir.getReturnValue(), gameTime);
    }
}
