package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4OfferFingerprint;
import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.KnownTraderMarketObservation;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/** Passive proof that the production V2 board-observation seam saw the fixture offer. */
@Mixin(KnownTraderMarketObservation.class)
public abstract class V4KnownTraderObservationMixin {

    @Inject(method = "recordVanillaBoard", at = @At("RETURN"))
    private static void spmscavenger_validation$observeBoard(
            ServerLevel level, UUID mobId, Villager villager, List<OfferSnapshot> board,
            CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.observeKnownTraderObservation(
                mobId, villager.getUUID(), Boolean.TRUE.equals(cir.getReturnValue()),
                level.getGameTime());
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        board.stream()
                .filter(offer -> offer.result().is(Items.IRON_PICKAXE))
                .findFirst()
                .ifPresent(offer -> V4RuntimeWitnessTracker.observeBoard(
                        mobId, villager.getUUID(), V4OfferFingerprint.of(offer), level.getGameTime()));
    }
}
