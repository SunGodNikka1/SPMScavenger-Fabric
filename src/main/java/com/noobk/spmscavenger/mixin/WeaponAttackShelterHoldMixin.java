package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.compat.OptionalGoalMobResolver;
import com.noobk.spmscavenger.goal.ShelterActivityEnvelope;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional SPM hook preventing a passive hunt/proactive target from masquerading as danger. */
@Pseudo
@Mixin(targets = {
        "games.brennan.playermob.entity.goal.WeaponAwareAttackGoal",
        "games.brennan.playermob.entity.goal.FlintAndSteelIgniteGoal",
        "games.brennan.playermob.entity.goal.TntCombatGoal",
        "games.brennan.playermob.entity.goal.EndCrystalCombatGoal"
})
public abstract class WeaponAttackShelterHoldMixin {

    @Inject(method = {"canUse", "method_6264", "canContinueToUse", "method_6266"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$requireShelterOverrideProvenance(
            CallbackInfoReturnable<Boolean> cir) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "WeaponAwareAttackGoal");
        if (mob != null && !ShelterActivityEnvelope.permitsTargetedCombat(mob)) {
            cir.setReturnValue(false);
        }
    }
}
