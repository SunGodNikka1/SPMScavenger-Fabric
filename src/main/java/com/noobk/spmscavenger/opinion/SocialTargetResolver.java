package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * Task 44B — turns recent host admission evidence into a target-bound {@link SocialIntent}, and
 * re-checks that intent against live state on demand.
 *
 * <h2>Shape</h2>
 *
 * <pre>
 * recent host admission evidence
 *   + bounded target search   (SPM's own, not ours)
 *   + current relationship legality
 *   + alive / loaded / same level / in range
 *        ↓
 *   SocialIntent(targetId)     ← re-validated again at adoption
 * </pre>
 *
 * <h2>Cost</h2>
 *
 * {@link #resolve} performs one bounded search per call, so it belongs on the director's decision
 * cadence, never on a per-tick path. The observed pulse rate was ~21/tick across a loaded set;
 * matching that with searches would be an unforced scan storm (Gate SPM-5). The search itself is
 * SPM's own and verified side-effect-free, so the cost is time, not state.
 */
public final class SocialTargetResolver {

    private SocialTargetResolver() {
    }

    /**
     * Attempt to name a social target for {@code mob} right now.
     *
     * <p>Returns empty for every rejection cause — the reason is available via
     * {@link #resolveWithReason} when a caller wants to explain itself rather than merely act.
     */
    public static Optional<SocialIntent> resolve(Mob mob, long now) {
        return resolveWithReason(mob, now).intent();
    }

    /** Resolution outcome carrying the cause, so "no target" is never an unexplained silence. */
    public record Resolution(Optional<SocialIntent> intent, SocialTargetValidity validity) {

        static Resolution rejected(SocialTargetValidity validity) {
            return new Resolution(Optional.empty(), validity);
        }
    }

    public static Resolution resolveWithReason(Mob mob, long now) {
        if (mob == null || !PlayerMobs.greetRelationsAvailable()) {
            return Resolution.rejected(SocialTargetValidity.SPM_UNAVAILABLE);
        }
        if (mob.getTarget() != null) {
            return Resolution.rejected(SocialTargetValidity.COMBAT_TARGET);
        }

        // The pulse is the ONLY source of the host's own acquisition radius. Choosing a radius here
        // would be inventing a constant SPM owns; reading the one it just used cannot drift.
        Optional<SocialAdmissionSeam.AdmissionWindow> window =
                SocialAdmissionSeam.admissionWindow(mob.getUUID(), now);
        if (window.isEmpty()) {
            return Resolution.rejected(SocialTargetValidity.NO_ADMISSION_EVIDENCE);
        }
        SocialAdmissionSeam.AdmissionWindow evidence = window.get();

        // Deliberately re-run the host's search instead of trusting the pulse's own
        // eligibleTargetFound: that flag is up to PULSE_LIFETIME_TICKS old and, decisively, never
        // carried an identity. "Somebody was greetable recently" cannot name whom to greet now.
        LivingEntity candidate = PlayerMobs.nearestGreetTarget(mob, evidence.range());
        if (candidate == null) {
            return Resolution.rejected(SocialTargetValidity.TARGET_GONE);
        }

        SocialTargetValidity validity = judge(mob, candidate, evidence.range(), true);
        if (!validity.usable()) {
            return Resolution.rejected(validity);
        }
        return new Resolution(
                Optional.of(new SocialIntent(
                        candidate.getUUID(), now, evidence.observedAtTick(), evidence.range())),
                SocialTargetValidity.VALID);
    }

    /**
     * Re-check an existing intent against live state — the call an executor must make before it acts
     * on one.
     *
     * <p>Re-resolves the id from the level rather than trusting anything remembered. A fresh
     * admission pulse is required again here, and in the real adoption path that is close to
     * tautological: the host only reaches its resolution call when it is genuinely willing, so the
     * pulse is contemporaneous with the attempt. When shelter or another authority holds the goal,
     * no pulse appears and adoption correctly fails closed.
     */
    public static SocialTargetValidity validate(Mob mob, SocialIntent intent, long now) {
        if (mob == null || intent == null || !PlayerMobs.greetRelationsAvailable()) {
            return SocialTargetValidity.SPM_UNAVAILABLE;
        }
        if (mob.getTarget() != null) {
            return SocialTargetValidity.COMBAT_TARGET;
        }
        boolean freshAdmission =
                SocialAdmissionSeam.admissionWindow(mob.getUUID(), now).isPresent();
        LivingEntity target = resolveLiving(mob, intent.targetId());
        if (!freshAdmission) {
            return SocialTargetValidity.NO_ADMISSION_EVIDENCE;
        }
        if (target == null) {
            return SocialTargetValidity.TARGET_GONE;
        }
        return judge(mob, target, intent.hostAcquisitionRange(), true);
    }

    /**
     * The shared tail of both paths — the only place the predicate is applied, so resolution and
     * re-validation cannot drift apart.
     *
     * <p>{@code hasFreshAdmission} is passed rather than re-derived because each caller has already
     * had to look the window up for its own reasons (resolution needs the host's radius out of it).
     * Both pass what they actually observed.
     */
    private static SocialTargetValidity judge(
            Mob mob, LivingEntity target, double range, boolean hasFreshAdmission) {
        Boolean greets = PlayerMobs.greetsToward(mob, target);
        return SocialTargetLegality.check(
                greets != null,
                mob.getTarget() != null,
                hasFreshAdmission,
                target != null,
                target != null && target.isAlive() && !target.isRemoved(),
                target != null && mob.level() == target.level(),
                target == null ? Double.NaN : mob.distanceToSqr(target),
                range * range,
                Boolean.TRUE.equals(greets));
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
