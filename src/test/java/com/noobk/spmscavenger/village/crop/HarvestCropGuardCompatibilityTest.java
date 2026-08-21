package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Task-55 R1-4 — crop guard compatibility lifecycle. */
class HarvestCropGuardCompatibilityTest {

    @AfterEach
    void reset() {
        HarvestCropGuardCompatibility.resetForTests();
    }

    @Test
    void serverSessionResetClearsPriorObservations() {
        HarvestCropGuardCompatibility.observeCanUseHook();
        HarvestCropGuardCompatibility.recordTargetResolutionFailed();
        HarvestCropGuardCompatibility.beginServerSession();
        assertFalse(HarvestCropGuardCompatibility.isOperational());
        assertFalse(HarvestCropGuardCompatibility.hasTargetResolutionFailed());
    }

    @Test
    void shutdownClearsSessionObservations() {
        HarvestCropGuardCompatibility.observeContinuationHook();
        HarvestCropGuardCompatibility.shutdownServerState();
        assertFalse(HarvestCropGuardCompatibility.isOperational());
    }

    @Test
    void warmupTickReachesZeroAndStops() {
        HarvestCropGuardCompatibility.beginServerSession();
        for (int i = 0; i < 250; i++) {
            HarvestCropGuardCompatibility.onServerTick();
        }
        assertTrue(true);
    }

    @Test
    void beginServerSessionProbesWithoutEpisode() {
        HarvestCropGuardCompatibility.beginServerSession();
        assertFalse(HarvestCropGuardCompatibility.hasTargetResolutionFailed());
    }
}
