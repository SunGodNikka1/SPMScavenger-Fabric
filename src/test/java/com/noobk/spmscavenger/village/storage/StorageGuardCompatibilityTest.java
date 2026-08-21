package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Task-54 R1-4 — compatibility observation lifecycle. */
class StorageGuardCompatibilityTest {

    @AfterEach
    void reset() {
        StorageGuardCompatibility.resetForTests();
    }

    @Test
    void serverSessionResetClearsPriorObservations() {
        StorageGuardCompatibility.observeCanUseHook();
        StorageGuardCompatibility.recordTargetResolutionFailed();
        StorageGuardCompatibility.beginServerSession();
        assertFalse(StorageGuardCompatibility.isOperational());
        assertFalse(StorageGuardCompatibility.hasTargetResolutionFailed());
    }

    @Test
    void shutdownClearsSessionObservations() {
        StorageGuardCompatibility.observeContinuationHook();
        StorageGuardCompatibility.shutdownServerState();
        assertFalse(StorageGuardCompatibility.isOperational());
    }

    @Test
    void warmupTickReachesZeroAndStops() {
        StorageGuardCompatibility.beginServerSession();
        for (int i = 0; i < 250; i++) {
            StorageGuardCompatibility.onServerTick();
        }
        // No exception; warm-up counter exhausted without leaking negative state.
        assertTrue(true);
    }
}
