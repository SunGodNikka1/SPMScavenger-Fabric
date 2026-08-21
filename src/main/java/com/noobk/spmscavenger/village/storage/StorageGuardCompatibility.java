package com.noobk.spmscavenger.village.storage;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostics-only wiring observations — never grants permission (D-VR-081).
 */
public final class StorageGuardCompatibility {

    public enum GuardObservation {
        HOST_SHAPE_SUPPORTED,
        CAN_USE_HOOK_OBSERVED,
        CONTINUATION_HOOK_OBSERVED,
        TARGET_RESOLUTION_FAILED
    }

    private static final int WARMUP_TICKS = 200;

    private static final AtomicBoolean HOST_SHAPE = new AtomicBoolean(false);
    private static final AtomicBoolean CAN_USE_HOOK = new AtomicBoolean(false);
    private static final AtomicBoolean CONTINUATION_HOOK = new AtomicBoolean(false);
    private static final AtomicBoolean TARGET_RESOLUTION_FAILED = new AtomicBoolean(false);
    private static volatile int warmupRemaining = -1;

    private StorageGuardCompatibility() {
    }

    public static void beginServerSession() {
        resetForSession();
        probeHostShapeOnServerStart();
        if (PlayerMobs.state() == PlayerMobs.State.FOUND) {
            warmupRemaining = WARMUP_TICKS;
        }
    }

    public static void onServerTick() {
        int remaining = warmupRemaining;
        if (remaining < 0) {
            return;
        }
        if (remaining == 0) {
            warmupRemaining = -1;
            logWarmupStatus();
            return;
        }
        warmupRemaining = remaining - 1;
    }

    public static void shutdownServerState() {
        warmupRemaining = -1;
        resetForSession();
    }

    public static void markHostShapeSupported() {
        HOST_SHAPE.set(true);
    }

    public static void observeCanUseHook() {
        CAN_USE_HOOK.set(true);
    }

    public static void observeContinuationHook() {
        CONTINUATION_HOOK.set(true);
    }

    public static void recordTargetResolutionFailed() {
        if (TARGET_RESOLUTION_FAILED.compareAndSet(false, true)) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] Ally storage guard fail-closed: could not read raid goal mob "
                            + "and/or targetPos — allies are denied until resolution succeeds.");
        }
    }

    public static boolean hasTargetResolutionFailed() {
        return TARGET_RESOLUTION_FAILED.get();
    }

    public static boolean isOperational() {
        return HOST_SHAPE.get() && CAN_USE_HOOK.get() && CONTINUATION_HOOK.get();
    }

    public static Set<GuardObservation> missingObservations() {
        EnumSet<GuardObservation> missing = EnumSet.noneOf(GuardObservation.class);
        if (!HOST_SHAPE.get()) {
            missing.add(GuardObservation.HOST_SHAPE_SUPPORTED);
        }
        if (!CAN_USE_HOOK.get()) {
            missing.add(GuardObservation.CAN_USE_HOOK_OBSERVED);
        }
        if (!CONTINUATION_HOOK.get()) {
            missing.add(GuardObservation.CONTINUATION_HOOK_OBSERVED);
        }
        return missing;
    }

    public static void probeHostShapeOnServerStart() {
        try {
            Class<?> goal = Class.forName("games.brennan.playermob.entity.goal.RaidContainersGoal");
            goal.getDeclaredField("targetPos");
            goal.getDeclaredMethod("canUse");
            goal.getDeclaredMethod("canContinueToUse");
            markHostShapeSupported();
        } catch (ReflectiveOperationException e) {
            SpmScavenger.LOGGER.debug(
                    "[spmscavenger] RaidContainersGoal shape probe skipped — SPM absent or changed.", e);
        }
    }

    public static void logWarmupStatus() {
        if (TARGET_RESOLUTION_FAILED.get()) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] Ally storage guard recorded target-resolution failure this session.");
        }
        if (isOperational()) {
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] Ally storage guard hooks observed — protection wiring operational.");
            return;
        }
        if (PlayerMobs.state() != PlayerMobs.State.FOUND) {
            return;
        }
        Set<GuardObservation> missing = missingObservations();
        if (!missing.isEmpty()) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] Ally storage guard UNVERIFIED after warm-up — missing: {}", missing);
        }
    }

    /** Test reset — not production API. */
    static void resetForTests() {
        resetForSession();
        warmupRemaining = -1;
    }

    private static void resetForSession() {
        HOST_SHAPE.set(false);
        CAN_USE_HOOK.set(false);
        CONTINUATION_HOOK.set(false);
        TARGET_RESOLUTION_FAILED.set(false);
    }
}
