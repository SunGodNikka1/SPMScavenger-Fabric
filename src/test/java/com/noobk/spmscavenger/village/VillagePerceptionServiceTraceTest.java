package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class VillagePerceptionServiceTraceTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    @Test
    void recordsSkippedEntityMissingSnapshot() {
        VillagePerceptionServiceTrace trace = new VillagePerceptionServiceTrace();
        UUID mobId = UUID.randomUUID();
        trace.record(OVERWORLD, mobId, 100L, false, false, 0, VillagePerceptionServiceTrace.RecordResult.SKIPPED);
        VillagePerceptionServiceTrace.Snapshot snapshot =
                trace.lastFor(OVERWORLD, mobId).orElseThrow();
        assertEquals(100L, snapshot.serviceTick());
        assertFalse(snapshot.entityResolved());
        assertEquals(VillagePerceptionServiceTrace.RecordResult.SKIPPED, snapshot.recordResult());
    }

    @Test
    void schedulerExposesLastServiceTrace() {
        VillagePerceptionScheduler scheduler = VillagePerceptionScheduler.createForTest(
                (dimension, level, mobId) -> {}, VillagePerceptionTuning.MAX_EMERGENCY_PENDING);
        UUID mobId = UUID.randomUUID();
        scheduler.recordServiceTrace(
                OVERWORLD,
                mobId,
                50L,
                true,
                true,
                7,
                VillagePerceptionServiceTrace.RecordResult.RECORDED);
        VillagePerceptionServiceTrace.Snapshot snapshot =
                scheduler.lastServiceTrace(OVERWORLD, mobId).orElseThrow();
        assertEquals(50L, snapshot.serviceTick());
        assertEquals(7, snapshot.observedPois());
        assertEquals(VillagePerceptionServiceTrace.RecordResult.RECORDED, snapshot.recordResult());
        assertEquals(50L, scheduler.lastGlobalServiceTick());
    }
}
