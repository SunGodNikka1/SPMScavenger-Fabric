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
    private static final Map<UUID, AdmissionWindow> WINDOWS = new ConcurrentHashMap<>();

    private static volatile MethodHandle nearestWhereReaction;

    private SocialAdmissionSeam() {
    }

    /**
     * @param eligibleTargetFound whether the host itself found a greet-eligible entity. Recorded
     *     because "SPM was willing to look" and "SPM found somebody" are different facts, and only
     *     the host may decide the second.
     */
    public record AdmissionWindow(long observedAtTick, double range, boolean eligibleTargetFound) {

        public boolean isFresh(long now) {
            return now - observedAtTick <= PULSE_LIFETIME_TICKS && now >= observedAtTick;
        }
    }

    public static void recordAdmissionWindow(Mob mob, double range, boolean eligibleTargetFound) {
        if (mob == null) {
            return;
        }
        WINDOWS.put(
                mob.getUUID(),
                new AdmissionWindow(mob.level().getGameTime(), range, eligibleTargetFound));
    }

    /** Non-allocating: never creates an experience context for a mob that has none. */
    public static java.util.Optional<AdmissionWindow> admissionWindow(UUID mobId, long now) {
        AdmissionWindow window = WINDOWS.get(mobId);
        if (window == null) {
            return java.util.Optional.empty();
        }
        if (!window.isFresh(now)) {
            WINDOWS.remove(mobId, window);
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
        if (mobId != null) {
            WINDOWS.remove(mobId);
        }
    }

    /** Gate RET-1: released with the world, like every other runtime-only map. */
    public static void shutdownServerState() {
        WINDOWS.clear();
        nearestWhereReaction = null;
    }

    public static int trackedWindowCount() {
        return WINDOWS.size();
    }

    public static boolean seamObserved(UUID mobId) {
        return WINDOWS.containsKey(mobId);
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
        WINDOWS.clear();
    }
}
