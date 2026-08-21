package com.noobk.spmscavenger.village.crop;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostics-only crop guard observations (task-55).
 */
public final class HarvestCropGuardCompatibility {

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

    private HarvestCropGuardCompatibility() {
    }

    public static void beginServerSession() {
        resetForSession();
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
                    "[spmscavenger] Managed crop veto could not read harvest goal targetPos.");
        }
    }

    public static Set<GuardObservation> observations() {
        EnumSet<GuardObservation> set = EnumSet.noneOf(GuardObservation.class);
        if (HOST_SHAPE.get()) {
            set.add(GuardObservation.HOST_SHAPE_SUPPORTED);
        }
        if (CAN_USE_HOOK.get()) {
            set.add(GuardObservation.CAN_USE_HOOK_OBSERVED);
        }
        if (CONTINUATION_HOOK.get()) {
            set.add(GuardObservation.CONTINUATION_HOOK_OBSERVED);
        }
        if (TARGET_RESOLUTION_FAILED.get()) {
            set.add(GuardObservation.TARGET_RESOLUTION_FAILED);
        }
        return set;
    }

    private static void resetForSession() {
        HOST_SHAPE.set(false);
        CAN_USE_HOOK.set(false);
        CONTINUATION_HOOK.set(false);
        TARGET_RESOLUTION_FAILED.set(false);
    }

    private static void logWarmupStatus() {
        if (!HOST_SHAPE.get()) {
            SpmScavenger.LOGGER.info(
                    "[spmscavenger] Crop guard warmup ended: host HarvestCropsGoal shape not yet "
                            + "probed (fail-open).");
            return;
        }
        SpmScavenger.LOGGER.info(
                "[spmscavenger] Crop guard warmup ended: canUse={}, continuation={}, "
                        + "targetResolutionFailed={}",
                CAN_USE_HOOK.get(),
                CONTINUATION_HOOK.get(),
                TARGET_RESOLUTION_FAILED.get());
    }
}
