package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.village.FirstHomePromotion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the real post-startSleeping production promotion boundary. */
@Mixin(FirstHomePromotion.class)
public abstract class V4FirstHomePromotionMixin {

    @Inject(method = "afterSuccessfulSleep", at = @At("RETURN"))
    private static void spmscavenger_validation$observePromotion(
            ServerLevel level, Mob mob, BlockPos bedPos,
            CallbackInfoReturnable<Boolean> cir) {
        V4RuntimeWitnessTracker.observeHomePromotion(
                mob.getUUID(),
                new V4RuntimeWitnessTracker.BlockPosEvidence(
                        bedPos.getX(), bedPos.getY(), bedPos.getZ()),
                mob.isSleeping(), Boolean.TRUE.equals(cir.getReturnValue()), level.getGameTime());
    }
}
