package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.MandatoryHandoffPolicy;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the production policy result; it neither creates nor extends the yield window. */
@Mixin(MandatoryHandoffPolicy.class)
public abstract class V4MandatoryHandoffLivenessMixin {

    @Inject(method = "yieldsToHandoff", at = @At("RETURN"))
    private static void spmscavenger_validation$yielded(
            Optional<MandatoryHandoffPolicy.HandoffPublication> published,
            Optional<GatherIntentPolicy.Resource> selectedFamily,
            MandatoryHandoffPolicy.YieldWindow current, long now,
            CallbackInfoReturnable<Optional<MandatoryHandoffPolicy.YieldWindow>> cir) {
        V4TradeLivenessWitness.observeGatherYield(cir.getReturnValue().isPresent(), now);
    }
}
