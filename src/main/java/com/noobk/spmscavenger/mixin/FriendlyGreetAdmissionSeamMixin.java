package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.compat.OptionalGoalMobResolver;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.SocialAdmissionSeam;
import com.noobk.spmscavenger.village.trade.TradeSessionClaimWindow;
import com.noobk.spmscavenger.opinion.SocialExecutionBindingRegistry;
import com.noobk.spmscavenger.opinion.SocialGreetClaimWindow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * Task 44A/44D — host admission observation plus exact Opinion-owned executor binding.
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
 * Reverified against pinned {@code playermob-0.96.0} source/artifact, {@code canUse()} is:
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
 *
 * <h2>Why {@code @Coerce}</h2>
 *
 * An INVOKE redirect handler must match the redirected method's signature with the invocation owner
 * prepended. The addon deliberately does not compile against SPM, so {@code PlayerMobEntity} and
 * {@code Reaction} cannot be named - {@code @Coerce} is the documented mechanism for accepting an
 * otherwise inaccessible reference argument as {@code Object}. Without it the target INVOKE can
 * exist, the build can pass, and the injector still rejects the handler descriptor at load - which
 * is precisely the failure Task 44A exists to detect rather than ship.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.FriendlyGreetGoal", remap = false)
public abstract class FriendlyGreetAdmissionSeamMixin {

    @Redirect(
            method = {"canUse", "method_6264"},
            at = @At(
                    value = "INVOKE",
                    target = "Lgames/brennan/playermob/entity/PlayerMobEntity;"
                            + "nearestWhereReaction(Lgames/brennan/playermob/entity/Reaction;D)"
                            + "Lnet/minecraft/world/entity/LivingEntity;"),
            require = 0)
    private LivingEntity spmscavenger$observeGreetAdmission(
            @Coerce Object playerMob, @Coerce Object reaction, double range) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "greet admission seam");
        LivingEntity original = SocialAdmissionSeam.invokeOriginal(playerMob, reaction, range);

        // V2-E trade interlock, and it must sit HERE - before the target is published into the
        // SOCIAL control plane. Bolted onto the end instead, the sequence would be:
        //     trade owns Bob -> SPM says Bob is greetable -> recordObservation(Bob)
        //     -> Opinion forms SOCIAL/Bob -> interlock finally returns null
        // The physical greet never happens, but cognitive work has been manufactured for an
        // executor we deliberately made unavailable. Suppressing before publication keeps trade
        // out of Opinion entirely.
        //
        // Narrow on purpose: this is NOT 44D-R2's global veto. It suppresses exactly one
        // (mob, villager) pairing while that mob is actively trying to trade with that villager.
        // Alice is untouched, other mobs greeting Bob are untouched, and Bob becomes greetable
        // again the moment the attempt ends.
        if (mob != null && original != null
                && TradeSessionClaimWindow.claims(
                        mob.getUUID(), original.getUUID(), mob.level().getGameTime())) {
            return null;
        }

        if (mob != null) {
            // Task 44A publishes the observation and returns the host's own answer unchanged.
            // Substituting a bound target is 44B+, and must additionally require
            // mayStartExecutor(SOCIAL) or it reintroduces the Task 43R defect.
            // Hand over SPM's own chosen target, not a boolean about it. This is the single
            // reason no later step needs to re-run the host's search or query its relationship API.
            SocialAdmissionSeam.recordObservation(
                    mob, range, original == null ? null : original.getUUID());
        }
        // BUG 1: indefinite null veto deleted native greeting. BUG 2 (VR-T1.5c): immediate return
        // original raced the 10-tick observer — greet started as SOCIAL_REFLEX before Opinion could
        // bind. Bounded claim window: defer briefly, then proceed unbound on timeout.
        if (mob == null || original == null) {
            if (mob != null) {
                SocialGreetClaimWindow.clear(mob.getUUID());
                SocialExecutionBindingRegistry.rejectAdmission(mob.getUUID());
            }
            return original;
        }
        if (OpinionFeatureGate.isEnabled()) {
            long gameTime = mob.level().getGameTime();
            boolean bound = SocialExecutionBindingRegistry.admit(
                    mob, original.getUUID(), gameTime).isPresent();
            return switch (SocialGreetClaimWindow.evaluate(
                    mob.getUUID(), original.getUUID(), gameTime, bound)) {
                case DEFER -> null;
                case PROCEED -> original;
            };
        }
        return original;
    }

    @Inject(method = {"start", "method_6269"}, at = @At("TAIL"), require = 0)
    private void spmscavenger$startBoundGreet(CallbackInfo ci) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "greet execution start");
        if (mob != null) {
            SocialExecutionBindingRegistry.started(mob, mob.level().getGameTime());
        }
    }

    /**
     * The pinned methods load {@code Phase.DONE} exactly six times (three per method). We target the
     * enum constant rather than every {@code phase} write because {@code tickGift()} also writes
     * {@code Phase.FETCH}; treating that transition as completion would create false learning.
     */
    @Inject(
            method = {"tickGift", "tickFetch"},
            at = @At(
                    value = "FIELD",
                    target = "Lgames/brennan/playermob/entity/goal/FriendlyGreetGoal$Phase;"
                            + "DONE:Lgames/brennan/playermob/entity/goal/FriendlyGreetGoal$Phase;",
                    opcode = Opcodes.GETSTATIC,
                    shift = At.Shift.BEFORE),
            require = 0)
    private void spmscavenger$observeHostCompletion(CallbackInfo ci) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "greet completion evidence");
        if (mob != null) {
            SocialExecutionBindingRegistry.completionObserved(mob);
        }
    }

    @Inject(method = {"stop", "method_6270"}, at = @At("HEAD"), require = 0)
    private void spmscavenger$finishBoundGreet(CallbackInfo ci) {
        Mob mob = OptionalGoalMobResolver.resolve(this, "greet execution stop");
        if (mob != null) {
            SocialExecutionBindingRegistry.stopped(mob, mob.level().getGameTime());
        }
    }
}
