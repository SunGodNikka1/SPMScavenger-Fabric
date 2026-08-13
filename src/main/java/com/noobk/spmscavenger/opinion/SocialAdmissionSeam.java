package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 44A — host-admission readiness, observed rather than probed.
 *
 * <h2>Why a pulse and not a cooldown accessor</h2>
 *
 * SPM reaches its target-resolution call only after {@code cooldownTicks == 0} and
 * {@code getTarget() == null} have both passed. So witnessing that call <em>is</em> the readiness
 * signal, with no private field exposed and nothing mutated (D-GAO-057). Probing {@code canUse()}
 * instead would decrement the cooldown and assign a target — the question would change its own
 * answer.
 *
 * <h2>Bounded by construction</h2>
 *
 * One record per mob, overwritten in place, and only for mobs SPM actually ticks. It is read through
 * the <b>non-allocating</b> registry query, never {@code contextFor(...)}, so observing a mob that
 * has no context cannot create one (Gate RET-1a).
 */
public final class SocialAdmissionSeam {

    /**
     * How long a witnessed admission attempt stays meaningful.
     *
     * <p>Must comfortably bridge the director's 10-tick observation cadence — the pulse exists so a
     * decision taken on the observer's schedule can know the executor was recently willing. Short
     * enough that a stale window cannot authorize a much later adoption.
     */
    public static final int PULSE_LIFETIME_TICKS = 40;

    /** Runtime only: a witnessed scheduler attempt has no meaning across a restart. */
    private static final Map<UUID, AdmissionObservation> OBSERVATIONS = new ConcurrentHashMap<>();

    private static volatile MethodHandle nearestWhereReaction;

    private SocialAdmissionSeam() {
    }

    /**
     * What the host actually decided, kept whole.
     *
     * <h2>Why the identity and not a boolean</h2>
     *
     * The redirect sits on the return of SPM's own {@code nearestWhereReaction(GREET, range)}, so it
     * receives <b>the exact entity SPM chose</b>. Reducing that to {@code targetFound = true} threw
     * away the answer and forced a later re-run of the same search purely to recover the identity we
     * had already been handed — duplicated work, and worse: {@code reactionToward} transitively
     * writes a per-tick memo cache, so asking again could warm it early and change the host's own
     * later answer within that tick. Keeping the UUID removes the second search, and with it the
     * only reason this addon had to touch an impure host method at all (D-GAO-057).
     *
     * <p>A returned target is also <em>proof of greet legality at that moment</em> — SPM's search
     * already applied its own relationship predicate. So no relationship question of ours remains.
     *
     * @param targetId the entity SPM selected, or {@code null} when it found nobody. Null is the
     *     common case: 98.4% of observed admissions named no target.
     */
    public record AdmissionObservation(long observedAtTick, double range, UUID targetId) {

        public boolean isFresh(long now) {
            return now - observedAtTick <= PULSE_LIFETIME_TICKS && now >= observedAtTick;
        }

        /** Whether the host named somebody. Presence of an identity is the whole signal. */
        public boolean hasTarget() {
            return targetId != null;
        }
    }

    /**
     * @param targetId exactly what SPM's search returned — never re-derived, never inferred
     */
    public static void recordObservation(Mob mob, double range, UUID targetId) {
        if (mob == null) {
            return;
        }
        OBSERVATIONS.put(
                mob.getUUID(),
                new AdmissionObservation(mob.level().getGameTime(), range, targetId));
    }

    /** Non-allocating: never creates an experience context for a mob that has none. */
    public static java.util.Optional<AdmissionObservation> observation(UUID mobId, long now) {
        AdmissionObservation window = OBSERVATIONS.get(mobId);
        if (window == null) {
            return java.util.Optional.empty();
        }
        if (!window.isFresh(now)) {
            OBSERVATIONS.remove(mobId, window);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(window);
    }

    /**
     * Gate RET-1a: released at <b>entity</b> lifetime, not merely world lifetime.
     *
     * <p>Eviction on read alone bounded the map by "mobs still being queried"; an unloaded or dead
     * mob left its final window resident for the rest of the session, because nothing would ever ask
     * about it again. The bound must be currently relevant mobs, not every PlayerMob that ever
     * produced a pulse.
     */
    public static void release(UUID mobId) {
        if (mobId == null) {
            return;
        }
        OBSERVATIONS.remove(mobId);
    }

    /** Gate RET-1: released with the world, like every other runtime-only map. */
    public static void shutdownServerState() {
        OBSERVATIONS.clear();
        nearestWhereReaction = null;
    }

    public static int trackedWindowCount() {
        return OBSERVATIONS.size();
    }

    public static boolean seamObserved(UUID mobId) {
        return OBSERVATIONS.containsKey(mobId);
    }

    /**
     * Calls the host method the redirect replaced, so the observation changes nothing.
     *
     * <p>Reflective because the addon does not compile against SPM. A resolution failure returns
     * {@code null}, which is what {@code canUse()} would see if no eligible target existed — the
     * safe direction: no greet starts, and nothing is ever falsely attributed.
     */
    public static LivingEntity invokeOriginal(Object playerMob, Object reaction, double range) {
        if (playerMob == null) {
            return null;
        }
        try {
            MethodHandle handle = nearestWhereReaction;
            if (handle == null) {
                // Resolve against the class that DECLARES the method and the enum's own class, not
                // whichever concrete instance happened to be observed first. A cached handle keyed
                // to an accidental subclass would quietly fail for any other one.
                Class<?> owner = declaringClassOf(playerMob.getClass(), "nearestWhereReaction");
                Class<?> reactionType = reaction.getClass().isEnum()
                        ? reaction.getClass()
                        : reaction.getClass().getSuperclass();
                handle = MethodHandles.publicLookup().findVirtual(
                        owner,
                        "nearestWhereReaction",
                        MethodType.methodType(LivingEntity.class, reactionType, double.class));
                nearestWhereReaction = handle;
            }
            return (LivingEntity) handle.invoke(playerMob, reaction, range);
        } catch (Throwable resolutionFailed) {
            return null;
        }
    }

    /** Walks to the type that actually declares the method, so the cache is subclass-independent. */
    private static Class<?> declaringClassOf(Class<?> from, String method) {
        for (Class<?> type = from; type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (java.lang.reflect.Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(method)) {
                    return type;
                }
            }
        }
        return from;
    }

    /** Test seam. */
    static void clearForTest() {
        OBSERVATIONS.clear();
    }
}
