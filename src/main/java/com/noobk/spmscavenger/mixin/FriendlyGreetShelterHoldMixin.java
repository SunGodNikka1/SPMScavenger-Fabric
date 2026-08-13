package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.goal.ShelterActivityEnvelope;
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

    @Inject(method = {"canUse", "method_6264", "canContinueToUse", "method_6266"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$holdShelterDuringMovingGreeting(
            CallbackInfoReturnable<Boolean> cir) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "voluntary travel");
        if (mob != null
                && !ShelterActivityEnvelope.permitsCandidate(
                        mob, ActivityClass.SOCIAL_REFLEX, true)) {
            cir.setReturnValue(false);
        }
    }
}
