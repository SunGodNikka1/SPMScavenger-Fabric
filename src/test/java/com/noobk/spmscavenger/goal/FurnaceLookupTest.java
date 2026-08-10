package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-1 — phased furnace search must not conflate deferred scans with confirmed absence. */
class FurnaceLookupTest {

    private static final int SCAN_INTERVAL = 80;
    private static final int SCAN_PHASE_SALT = 53;
    private static final int FAILED_COOLDOWN = 100;

    @Test
    void mustNotHappen_deferredScanDoesNotAuthorizeFurnacePlacement() {
        PhasedScanClock clock = new PhasedScanClock(2, SCAN_INTERVAL, SCAN_PHASE_SALT);
        AtomicBoolean scanned = new AtomicBoolean(false);
        BlockPos nearbyFurnace = new BlockPos(8, 64, 8);

        FurnaceLookup.Resolution resolution = FurnaceLookup.resolve(
                0L,
                0L,
                null,
                true,
                clock,
                pos -> true,
                () -> {
                    scanned.set(true);
                    return nearbyFurnace;
                },
                FAILED_COOLDOWN);

        assertEquals(FurnaceLookup.Outcome.DEFERRED, resolution.result().outcome());
        assertFalse(scanned.get(), "world cube must not run before the phased slot");
        assertFalse(resolution.result().authorizesFurnacePlacement(true, true));
    }

    @Test
    void mustHappen_completedScanWithNoFurnaceMayAuthorizePlacement() {
        int phase = PhasedScanClock.phaseFor(2, SCAN_INTERVAL, SCAN_PHASE_SALT);
        PhasedScanClock clock = new PhasedScanClock(2, SCAN_INTERVAL, SCAN_PHASE_SALT);
        AtomicBoolean scanned = new AtomicBoolean(false);

        FurnaceLookup.Resolution resolution = FurnaceLookup.resolve(
                phase,
                0L,
                null,
                true,
                clock,
                pos -> true,
                () -> {
                    scanned.set(true);
                    return null;
                },
                FAILED_COOLDOWN);

        assertEquals(FurnaceLookup.Outcome.ABSENT_RECENT, resolution.result().outcome());
        assertTrue(scanned.get(), "phased slot must perform the cube scan");
        assertTrue(resolution.result().authorizesFurnacePlacement(true, true));
        assertEquals(phase + FAILED_COOLDOWN, resolution.searchFailedUntilTick());
    }

    @Test
    void cachedUsableFurnaceReturnsFoundWithoutScanning() {
        PhasedScanClock clock = new PhasedScanClock(2, SCAN_INTERVAL, SCAN_PHASE_SALT);
        BlockPos cached = new BlockPos(4, 64, 4);
        AtomicBoolean scanned = new AtomicBoolean(false);

        FurnaceLookup.Resolution resolution = FurnaceLookup.resolve(
                0L,
                0L,
                cached,
                true,
                clock,
                cached::equals,
                () -> {
                    scanned.set(true);
                    return null;
                },
                FAILED_COOLDOWN);

        assertEquals(FurnaceLookup.Outcome.FOUND, resolution.result().outcome());
        assertEquals(cached, resolution.result().position());
        assertFalse(scanned.get());
    }
}
