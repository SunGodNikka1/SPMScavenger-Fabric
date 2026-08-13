package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.opinion.SocialAdmissionSeam;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Task 44A — adapter seam proof. <b>Observation only: this changes no behaviour.</b>
 *
 * <h2>What it is proving</h2>
 *
 * Whether we can interpose on {@code FriendlyGreetGoal}'s native admission path without breaking the
 * existing shelter {@code @Inject(HEAD)} hook or vanilla-SPM parity. Nothing here selects a target,
 * cancels a greet, or creates a {@code SocialIntent} — GAO-10's executor direction rests on this
 * seam existing, so the seam is established before anything is built on it.
 *
 * <h2>Why this call site</h2>
 *
 * Verified against pinned {@code playermob-0.86.0} bytecode, {@code canUse()} is:
 *
 * <pre>
 * if (cooldownTicks > 0) { cooldownTicks--; return false; }
 * if (mob.getTarget() != null) return false;
 * LivingEntity found = mob.nearestWhereReaction(GREET, range);   &lt;-- redirected here
 * if (found == null) return false;
 * this.friend = found;
 * return true;
 * </pre>
 *
 * Reaching this call means SPM's own cooldown and combat gates have already passed, so the redirect
 * doubles as a truthful <b>host-admission-readiness pulse</b> — observed from a real scheduler
 * attempt rather than by probing {@code canUse()}, which mutates (D-GAO-057).
 *
 * <h2>Coexistence</h2>
 *
 * {@code FriendlyGreetShelterHoldMixin} injects at {@code HEAD} of the same method and can cancel.
 * A HEAD cancel returns before this call site, so the two operate at different bytecode positions:
 * shelter decides first, and only if it permits does execution reach the redirect.
 *
 * <p>{@code require = 0} throughout: if SPM restructures the call away, the mixin silently does not
 * apply, the pulse is never published, and SOCIAL can never be adopted. Adapter failure loses
 * control; it can never manufacture ownership (D-GAO-058).
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.FriendlyGreetGoal", remap = false)
public abstract class FriendlyGreetAdmissionSeamMixin {

    @Redirect(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lgames/brennan/playermob/entity/PlayerMobEntity;"
                            + "nearestWhereReaction(Lgames/brennan/playermob/entity/Reaction;D)"
                            + "Lnet/minecraft/world/entity/LivingEntity;"),
            require = 0)
    private LivingEntity spmscavenger$observeGreetAdmission(
            Object playerMob, Object reaction, double range) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "greet admission seam");
        LivingEntity original = SocialAdmissionSeam.invokeOriginal(playerMob, reaction, range);
        if (mob != null) {
            // Task 44A publishes the observation and returns the host's own answer unchanged.
            // Substituting a bound target is 44B+, and must additionally require
            // mayStartExecutor(SOCIAL) or it reintroduces the Task 43R defect.
            SocialAdmissionSeam.recordAdmissionWindow(mob, range, original != null);
        }
        return original;
    }
}
