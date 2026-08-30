package com.noobk.spmscavenger.validation.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.noobk.spmscavenger.validation.V4OfferFingerprint;
import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.village.trade.VillagerTradeAdapter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Transaction-boundary observation of the exact live offer production committed. */
@Mixin(VillagerTradeAdapter.class)
public abstract class V4VillagerTradeAdapterMixin {

    @Inject(method = "performResolvedTrade", at = @At("HEAD"))
    private static void spmscavenger_validation$captureTradeFingerprint(
            Container backpack, Villager villager, MerchantOffer live,
            CallbackInfoReturnable<VillagerTradeAdapter.TradeResult> cir,
            @Share("preTradeFingerprint") LocalRef<V4OfferFingerprint> preTradeFingerprint) {
        preTradeFingerprint.set(V4OfferFingerprint.of(live));
    }

    @Inject(method = "performResolvedTrade", at = @At("RETURN"))
    private static void spmscavenger_validation$observeTrade(
            Container backpack, Villager villager, MerchantOffer live,
            CallbackInfoReturnable<VillagerTradeAdapter.TradeResult> cir,
            @Share("preTradeFingerprint") LocalRef<V4OfferFingerprint> preTradeFingerprint) {
        V4RuntimeWitnessTracker.observeTrade(
                backpack, villager.getUUID(), preTradeFingerprint.get(),
                cir.getReturnValue() == VillagerTradeAdapter.TradeResult.TRADED,
                villager.level().getGameTime());
    }
}
