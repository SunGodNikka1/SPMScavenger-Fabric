package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.village.interaction.CommuteDirective;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Confirms that the existing ExploringGoal seeded its existing COMMUTE executor. */
@Mixin(ExploringGoal.class)
public abstract class V4ExploringGoalWitnessMixin {

    @Shadow @Final private PathfinderMob mob;

    @Inject(method = "seedRequiredTradeCommuteExpedition", at = @At("RETURN"))
    private void spmscavenger_validation$observeCommuteSeed(
            ServerLevel level, long now, CommuteDirective directive,
            CallbackInfoReturnable<Boolean> cir) {
        V4RuntimeWitnessTracker.observeCommuteSeed(
                mob.getUUID(), directive.binding().intent(),
                Boolean.TRUE.equals(cir.getReturnValue()), now);
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void spmscavenger_validation$observeNavigationDiscard(CallbackInfo ci) {
        if (mob.level() instanceof ServerLevel level) {
            V4RuntimeWitnessTracker.observeNavigationStop(mob.getUUID(), level.getGameTime());
        }
    }
}
