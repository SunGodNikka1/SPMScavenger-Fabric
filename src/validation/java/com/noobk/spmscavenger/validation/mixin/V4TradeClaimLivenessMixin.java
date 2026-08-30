package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive claim/release callbacks for the exact production interlock calls. */
@Mixin(TradeSessionClaimWindow.class)
public abstract class V4TradeClaimLivenessMixin {

    @Inject(method = "claim", at = @At("RETURN"))
    private static void spmscavenger_validation$claim(
            UUID mobId, UUID villagerId, long gameTime, CallbackInfo ci) {
        V4TradeLivenessWitness.observeTradeClaim(mobId, villagerId, true, gameTime);
    }

    @Inject(method = "release", at = @At("RETURN"))
    private static void spmscavenger_validation$release(UUID mobId, CallbackInfo ci) {
        V4TradeLivenessWitness.observeTradeClaim(mobId, null, false, -1L);
    }
}
