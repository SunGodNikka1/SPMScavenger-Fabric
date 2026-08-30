package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.trade.TradeMarketDiscoveryCooldown;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive callbacks around the production negative-market cooldown. */
@Mixin(TradeMarketDiscoveryCooldown.class)
public abstract class V4TradeMarketCooldownMixin {

    @Shadow private long retryAtTick;

    @Inject(method = "coolingDown", at = @At("RETURN"))
    private void spmscavenger_validation$cooldownCheck(
            ResourceLocation consumerKey, ResourceLocation materialKey, long gameTime,
            CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.observeCooldownCheck(
                this, cir.getReturnValue(), retryAtTick, gameTime);
    }

    @Inject(method = "recordEmpty", at = @At("RETURN"))
    private void spmscavenger_validation$emptyRecorded(
            ResourceLocation consumerKey, ResourceLocation materialKey, long gameTime,
            CallbackInfo ci) {
        V4TradeLivenessWitness.observeCooldownRecorded(this, retryAtTick, gameTime);
    }
}
