package com.noobk.spmscavenger.opinion;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VR-T1.5c — bounded defer so Opinion can claim a greet before SPM starts it.
 *
 * <p>Without a claim window, {@link SocialExecutionBindingRegistry#admit} races the director: the
 * pulse is published on greet {@code canUse}, but the observer that may form a SOCIAL intent runs
 * on a separate {@value DiscretionaryDirectorConstants#OPINION_OBSERVE_INTERVAL_TICKS}-tick cadence.
 * Immediate {@code return original} lets the greet start as {@code SOCIAL_REFLEX} before Opinion
 * can bind. An indefinite veto deleted native greeting (BUG 1); this window is the bounded middle
 * ground.
 */
public final class SocialGreetClaimWindow {

    public enum Outcome {
        /** Greet not ready yet — {@code canUse} should see no target this tick. */
        DEFER,
        /** Binding exists or the claim window expired — hand back SPM's chosen target. */
        PROCEED
    }

    private record Pending(UUID targetId, long openedAtTick, long deadlineTick) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private SocialGreetClaimWindow() {}

    /**
     * @param bound whether {@link SocialExecutionBindingRegistry#admit} succeeded this tick for
     *     {@code targetId}
     */
    public static Outcome evaluate(UUID mobId, UUID targetId, long gameTime, boolean bound) {
        if (mobId == null || targetId == null) {
            return Outcome.PROCEED;
        }
        if (bound) {
            clear(mobId);
            return Outcome.PROCEED;
        }

        Pending pending = PENDING.get(mobId);
        if (pending == null || !pending.targetId().equals(targetId)) {
            long deadline = gameTime + DiscretionaryDirectorConstants.GREET_CLAIM_WINDOW_TICKS;
            PENDING.put(mobId, new Pending(targetId, gameTime, deadline));
            return gameTime >= deadline ? Outcome.PROCEED : Outcome.DEFER;
        }
        if (gameTime >= pending.deadlineTick()) {
            clear(mobId);
            return Outcome.PROCEED;
        }
        return Outcome.DEFER;
    }

    public static void clear(UUID mobId) {
        if (mobId != null) {
            PENDING.remove(mobId);
        }
    }

    /** Gate RET-1a — same lifetime as {@link SocialAdmissionSeam} and binding registry. */
    public static void release(UUID mobId) {
        clear(mobId);
    }

    public static void shutdownServerState() {
        PENDING.clear();
    }

    static int trackedClaimCount() {
        return PENDING.size();
    }

    /** Test seam. */
    static void clearForTest() {
        PENDING.clear();
    }
}
