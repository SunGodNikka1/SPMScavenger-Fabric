package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.goal.TradeWithVillagerGoal;
import com.noobk.spmscavenger.validation.V4TradeLivenessWitness;
import com.noobk.spmscavenger.village.trade.TradeMarketDiscoveryCooldown;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the real TradeWithVillagerGoal lifecycle without invoking any Goal method. */
@Mixin(TradeWithVillagerGoal.class)
public abstract class V4TradeGoalLivenessMixin {

    @Shadow @Final private Mob mob;
    @Shadow @Final private TradeMarketDiscoveryCooldown marketDiscoveryCooldown;

    @Inject(method = "canUse", at = @At("HEAD"))
    private void spmscavenger_validation$tradeCanUseHead(CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.enterTradeCanUse(
                mob.getUUID(), marketDiscoveryCooldown, tick());
    }

    @Inject(method = "canUse", at = @At("RETURN"))
    private void spmscavenger_validation$tradeCanUseReturn(CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.exitTradeCanUse(mob.getUUID(), cir.getReturnValue(), tick());
    }

    @Inject(method = "liveDemand", at = @At("RETURN"))
    private void spmscavenger_validation$tradeDemand(
            ServerLevel level,
            CallbackInfoReturnable<Optional<WorkDemandPolicy.MaterialDemand>> cir) {
        V4TradeLivenessWitness.observeTradeDemand(
                mob.getUUID(), cir.getReturnValue().orElse(null), level.getGameTime());
    }

    @Inject(method = "existingRouteInfeasible", at = @At("RETURN"))
    private void spmscavenger_validation$routeGate(
            ServerLevel level, WorkDemandPolicy.MaterialDemand demand,
            CallbackInfoReturnable<Boolean> cir) {
        V4TradeLivenessWitness.observeTradeRouteGate(
                mob.getUUID(), demand, cir.getReturnValue(), level.getGameTime());
    }

    @Inject(method = "authorizedCandidate", at = @At("HEAD"))
    private void spmscavenger_validation$candidateHead(
            ServerLevel level, Villager carriedBuyer, CallbackInfoReturnable<Optional<?>> cir) {
        V4TradeLivenessWitness.enterAuthorizedCandidate(mob.getUUID(), level.getGameTime());
    }

    @Inject(method = "authorizedCandidate", at = @At("RETURN"))
    private void spmscavenger_validation$candidateReturn(
            ServerLevel level, Villager carriedBuyer, CallbackInfoReturnable<Optional<?>> cir) {
        V4TradeLivenessWitness.exitAuthorizedCandidate(
                mob.getUUID(), cir.getReturnValue().isPresent(), level.getGameTime());
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void spmscavenger_validation$start(CallbackInfo ci) {
        V4TradeLivenessWitness.observeTradeStart(mob.getUUID(), tick());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void spmscavenger_validation$tick(CallbackInfo ci) {
        V4TradeLivenessWitness.observeTradeTick(mob.getUUID());
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void spmscavenger_validation$stop(CallbackInfo ci) {
        V4TradeLivenessWitness.observeTradeStop(mob.getUUID(), tick());
    }

    private long tick() {
        return mob.level() instanceof ServerLevel level ? level.getGameTime() : -1L;
    }
}
