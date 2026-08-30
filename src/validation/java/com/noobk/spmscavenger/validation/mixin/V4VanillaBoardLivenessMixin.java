package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.TradeOpportunityQuery;
import com.noobk.spmscavenger.village.trade.VanillaTradeSource;
import java.util.List;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the exact production board-read call; the returned snapshots are not reinterpreted. */
@Mixin(VanillaTradeSource.class)
public abstract class V4VanillaBoardLivenessMixin {

    @Inject(method = "offers", at = @At("RETURN"))
    private void spmscavenger_validation$boardRead(
            Villager villager, TradeOpportunityQuery query,
            CallbackInfoReturnable<List<OfferSnapshot>> cir) {
        if (villager != null) {
            V4TradeLivenessWitness.observeVanillaBoardRead(
                    villager.getUUID(), villager.level().getGameTime());
        }
    }
}
