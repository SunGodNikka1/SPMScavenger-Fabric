package com.noobk.spmscavenger.validation.mixin;

import com.noobk.spmscavenger.validation.V4RuntimeWitnessTracker;
import com.noobk.spmscavenger.village.interaction.CommuteDirective;
import com.noobk.spmscavenger.village.interaction.CommuteDirectiveEvaluation;
import com.noobk.spmscavenger.village.interaction.VillageInteractionDirector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/** Passive observations at the production director's authorization/release boundaries. */
@Mixin(VillageInteractionDirector.class)
public abstract class V4VillageInteractionDirectorMixin {

    @Inject(method = "openOrResumeRequiredTrade", at = @At("RETURN"))
    private static void spmscavenger_validation$observeDirective(
            ServerLevel level, PathfinderMob mob,
            CallbackInfoReturnable<Optional<CommuteDirective>> cir) {
        cir.getReturnValue().ifPresent(directive -> V4RuntimeWitnessTracker.observeDirective(
                mob.getUUID(), directive.binding().intent(), level.getGameTime()));
    }

    @Inject(method = "revalidateRequiredTrade", at = @At("RETURN"))
    private static void spmscavenger_validation$observeRevalidation(
            ServerLevel level, PathfinderMob mob, CommuteDirective.Binding expected,
            boolean interrupted, CallbackInfoReturnable<CommuteDirectiveEvaluation> cir) {
        CommuteDirectiveEvaluation result = cir.getReturnValue();
        if (result.state() == CommuteDirectiveEvaluation.State.INTERRUPTED) {
            V4RuntimeWitnessTracker.observeInterruption(
                    mob.getUUID(), expected.intent(), level.getGameTime());
        } else if (result.state() == CommuteDirectiveEvaluation.State.ACTIVE) {
            V4RuntimeWitnessTracker.observeResume(
                    mob.getUUID(), expected.intent(), level.getGameTime());
        }
    }

    @Inject(method = "completeArrival", at = @At("RETURN"))
    private static void spmscavenger_validation$observeArrival(
            UUID mobId, CommuteDirective.Binding binding,
            CallbackInfoReturnable<Boolean> cir) {
        V4RuntimeWitnessTracker.observeArrival(
                mobId, binding.intent(), Boolean.TRUE.equals(cir.getReturnValue()),
                -1L);
    }

    @Inject(method = "recordTerminalRouteFailure", at = @At("RETURN"))
    private static void spmscavenger_validation$observeRouteFailure(
            UUID mobId, CommuteDirective.Binding binding, long now,
            CallbackInfoReturnable<Boolean> cir) {
        V4RuntimeWitnessTracker.observeRouteFailure(
                mobId, binding.intent(), Boolean.TRUE.equals(cir.getReturnValue()), now);
    }
}
