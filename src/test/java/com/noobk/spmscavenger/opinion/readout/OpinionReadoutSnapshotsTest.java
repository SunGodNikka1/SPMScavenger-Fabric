package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpinionReadoutSnapshotsTest {

    @AfterEach
    void reset() {
        OpinionFeatureGate.clearTestOverride();
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void captureUsesFindBackedContextWithoutAllocatingNewRegistryEntry() {
        UUID mob = UUID.randomUUID();
        var context = OpinionExperienceRegistry.contextFor(mob);
        int before = OpinionExperienceRegistry.contextCount();

        OpinionReadoutSnapshot snapshot = OpinionReadoutSnapshots.capture(
                42L, 7, "TestMob", context, null);

        assertEquals(42L, snapshot.requestId());
        assertEquals("TestMob", snapshot.mobDisplayName());
        assertEquals(before, OpinionExperienceRegistry.contextCount());
        assertSame(context, OpinionExperienceRegistry.find(mob));
    }

    @Test
    void captureIfPresentDoesNotAllocateMissingContext() {
        UUID mob = UUID.randomUUID();
        OpinionReadoutSnapshot snapshot = OpinionReadoutSnapshots.captureIfPresent(
                1L, 2, "Ghost", mob, null).orElseThrow();
        if (PlayerMobs.available()) {
            assertEquals(OpinionReadoutStatus.NO_CONTEXT, snapshot.status());
        } else {
            assertEquals(OpinionReadoutStatus.SPM_UNAVAILABLE, snapshot.status());
        }
        assertEquals(0, OpinionExperienceRegistry.contextCount());
    }
}
