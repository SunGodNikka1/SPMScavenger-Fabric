package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.compat.OptionalGoalMobResolver;
import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.ShelterActivityEnvelope;
import com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional SPM hook for pinned voluntary host travel executors. */
@Pseudo
@Mixin(targets = {
        "games.brennan.playermob.entity.goal.FriendlyGreetGoal",
        "games.brennan.playermob.entity.goal.FollowLovedOneGoal",
        "games.brennan.playermob.entity.goal.SeekAmmoGoal",
        "games.brennan.playermob.entity.goal.RaidContainersGoal",
        "games.brennan.playermob.entity.goal.RaidArmorStandsGoal",
        "games.brennan.playermob.entity.goal.CollectFloorItemsGoal",
        "games.brennan.playermob.entity.goal.HarvestCropsGoal",
        "games.brennan.playermob.entity.goal.AdvanceCarriageGoal",
        "games.brennan.playermob.entity.goal.CrossGroupGapGoal"
})
public abstract class FriendlyGreetShelterHoldMixin {

    @Inject(method = {"canUse", "method_6264"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$holdShelterDuringMovingActivityAdmission(
            CallbackInfoReturnable<Boolean> cir) {
        spmscavenger$applyShelterEnvelope(ActivityClass.SOCIAL_REFLEX, cir);
    }

    @Inject(method = {"canContinueToUse", "method_6266"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$holdShelterDuringMovingActivityContinuation(
            CallbackInfoReturnable<Boolean> cir) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "voluntary travel");
        ActivityClass semanticClass = mob != null
                && this.getClass().getName().endsWith("FriendlyGreetGoal")
                ? SocialExecutionBindingRegistry.friendlyGreetActivityClass(mob.getUUID())
                : ActivityClass.SOCIAL_REFLEX;
        spmscavenger$applyShelterEnvelope(mob, semanticClass, cir);
    }

    private void spmscavenger$applyShelterEnvelope(
            ActivityClass semanticClass,
            CallbackInfoReturnable<Boolean> cir) {
        spmscavenger$applyShelterEnvelope(
                OptionalGoalMobResolver.resolve(this, "voluntary travel"), semanticClass, cir);
    }

    private static void spmscavenger$applyShelterEnvelope(
            Mob mob,
            ActivityClass semanticClass,
            CallbackInfoReturnable<Boolean> cir) {
        if (mob != null
                && !ShelterActivityEnvelope.permitsCandidate(
                        mob, semanticClass, true)) {
            cir.setReturnValue(false);
        }
    }
}
