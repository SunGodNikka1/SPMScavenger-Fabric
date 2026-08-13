package com.noobk.spmscavenger.opinion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * Task 44B/44C — turns a recent host admission observation into a target-bound {@link SocialIntent},
 * and re-checks that intent against live world state.
 *
 * <h2>Shape</h2>
 *
 * <pre>
 * SPM's own admission observation   (already carries the identity SPM chose)
 *   + cheap live entity checks      (resolvable / alive / same level / in range)
 *        ↓
 *   SocialIntent(targetId)          ← re-validated again at adoption
 * </pre>
 *
 * <h2>What this deliberately does not do</h2>
 *
 * It performs <b>no search and no relationship query</b>. Both were once here, and both were
 * redundant: the redirect already receives SPM's chosen entity, so re-running
 * {@code nearestWhereReaction} recovered an identity we had been handed, and {@code reactionToward}
 * — which transitively writes a per-tick memo cache — re-asked a question the host had already
 * answered. Removing them removes the only reason this addon touched an impure host method
 * (D-GAO-057), and removes a per-decision scan (SPM-5).
 *
 * <p>Consequently this class is cheap enough that cadence is a cost question again rather than a
 * correctness one — though it still belongs on the director's decision cadence, not a per-tick path.
 */
public final class SocialTargetResolver {

    private SocialTargetResolver() {
    }

    /** Resolution outcome carrying its cause, so "no target" is never an unexplained silence. */
    public record Resolution(Optional<SocialIntent> intent, SocialTargetValidity validity) {

        static Resolution rejected(SocialTargetValidity validity) {
            return new Resolution(Optional.empty(), validity);
        }
    }

    public static Optional<SocialIntent> resolve(Mob mob, long now) {
        return resolveWithReason(mob, now).intent();
    }

    public static Resolution resolveWithReason(Mob mob, long now) {
        if (mob == null) {
            return Resolution.rejected(SocialTargetValidity.NO_ADMISSION_EVIDENCE);
        }
        Optional<SocialAdmissionSeam.AdmissionObservation> observed =
                SocialAdmissionSeam.observation(mob.getUUID(), now);

        SocialAdmissionSeam.AdmissionObservation evidence = observed.orElse(null);
        UUID named = evidence == null ? null : evidence.targetId();
        LivingEntity target = named == null ? null : resolveLiving(mob, named);

        SocialTargetValidity validity = judge(
                mob, target, evidence == null ? Double.NaN : evidence.range(),
                evidence != null, named != null, true);
        if (!validity.usable()) {
            return Resolution.rejected(validity);
        }
        return new Resolution(
                Optional.of(new SocialIntent(
                        named, now, evidence.observedAtTick(), evidence.range())),
                SocialTargetValidity.VALID);
    }

    /**
     * Re-check an existing intent against live state.
     *
     * <p>This establishes that the remembered entity is still physically usable. It does <b>not</b>
     * establish permission to act: that requires the live redirect naming this exact target again
     * (44D). An intent that passes here has not been authorised, only kept alive.
     */
    public static SocialTargetValidity validate(Mob mob, SocialIntent intent, long now) {
        if (mob == null || intent == null) {
            return SocialTargetValidity.TARGET_GONE;
        }
        SocialAdmissionSeam.AdmissionObservation evidence =
                SocialAdmissionSeam.observation(mob.getUUID(), now).orElse(null);

        // The freshest observation must still name THIS subject. The record is one-per-mob and
        // overwritten, so "an observation exists" says nothing about whom it is about: at tick 100
        // the host names Bob and we form an intent; at tick 101 it names Alice, or nobody. Passing a
        // hardcoded `true` here (as the first version of this method did) let Bob's intent survive
        // on Alice's evidence — 44D's identity equality would still have refused to execute it, but
        // the control plane would have kept scoring a subject the host had already moved on from.
        boolean namesThisTarget =
                evidence != null && intent.targetId().equals(evidence.targetId());

        // Use the range from the observation that actually corroborates this target, not the one
        // captured when the intent was formed. Old evidence must not set the bound for a new check.
        double range = namesThisTarget ? evidence.range() : intent.hostAcquisitionRange();

        LivingEntity target = resolveLiving(mob, intent.targetId());
        return judge(
                mob, target, range,
                evidence != null,
                evidence != null && evidence.hasTarget(),
                namesThisTarget);
    }

    /**
     * The one place the predicate is applied, so discovery and re-validation cannot drift apart.
     */
    private static SocialTargetValidity judge(
            Mob mob,
            LivingEntity target,
            double range,
            boolean hasFreshObservation,
            boolean observationNamedTarget,
            boolean observationNamesThisTarget) {
        return SocialTargetLegality.check(
                mob.getTarget() != null,
                hasFreshObservation,
                observationNamedTarget,
                observationNamesThisTarget,
                target != null,
                target != null && target.isAlive() && !target.isRemoved(),
                target != null && mob.level() == target.level(),
                target == null ? Double.NaN : mob.distanceToSqr(target),
                range * range);
    }

    /** Level-scoped id lookup: a UUID from another dimension must not resolve to a live target. */
    private static LivingEntity resolveLiving(Mob mob, UUID targetId) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(targetId);
        return entity instanceof LivingEntity living ? living : null;
    }
}
