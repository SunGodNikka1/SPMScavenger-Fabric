package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityOpinionMemoryTest {

    @Test
    void recordOutcomeClampsPreference() {
        EntityOpinionMemory memory = new EntityOpinionMemory();
        UUID entity = UUID.randomUUID();

        memory.recordOutcome(entity, 200f);
        assertEquals(EntityOpinionMemory.PREFERENCE_MAX, memory.preference(entity));

        memory.recordOutcome(entity, -400f);
        assertEquals(EntityOpinionMemory.PREFERENCE_MIN, memory.preference(entity));
    }

    @Test
    void eldestEntryEvictedWhenOverCap() {
        EntityOpinionMemory memory = new EntityOpinionMemory();
        UUID first = UUID.randomUUID();
        memory.recordOutcome(first, 1f);

        for (int i = 0; i < EntityOpinionMemory.MAX_ENTRIES; i++) {
            memory.recordOutcome(UUID.randomUUID(), 1f);
        }

        assertEquals(EntityOpinionMemory.MAX_ENTRIES, memory.trackedEntityCount());
        assertEquals(0f, memory.preference(first), 0.001f);
    }

    @Test
    void snapshotRoundTripPreservesPreferences() {
        EntityOpinionMemory memory = new EntityOpinionMemory();
        UUID entity = UUID.randomUUID();
        memory.recordOutcome(entity, 12f);

        EntityOpinionMemory restored = new EntityOpinionMemory();
        restored.restoreFromSnapshot(memory.captureSnapshot());

        assertEquals(12f, restored.preference(entity), 0.001f);
        assertEquals(1, restored.trackedEntityCount());
    }

    @Test
    void clearRemovesAllEntries() {
        EntityOpinionMemory memory = new EntityOpinionMemory();
        memory.recordOutcome(UUID.randomUUID(), 3f);
        assertTrue(memory.trackedEntityCount() > 0);

        memory.clear();

        assertEquals(0, memory.trackedEntityCount());
    }
}
