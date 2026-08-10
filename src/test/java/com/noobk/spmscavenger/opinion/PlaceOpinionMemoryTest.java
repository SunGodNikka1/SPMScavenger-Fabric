package com.noobk.spmscavenger.opinion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceOpinionMemoryTest {

    @Test
    void unknownChunkReturnsNeutralPreference() {
        PlaceOpinionMemory memory = new PlaceOpinionMemory();
        assertEquals(0f, memory.preference(new ChunkPos(0, 0)));
    }

    @Test
    void recordsAndClampsPreference() {
        PlaceOpinionMemory memory = new PlaceOpinionMemory();
        long key = new ChunkPos(3, 4).toLong();

        memory.recordOutcome(key, 50f);
        memory.recordOutcome(key, 60f);

        assertEquals(PlaceOpinionMemory.PREFERENCE_MAX, memory.preference(key));
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        PlaceOpinionMemory memory = new PlaceOpinionMemory();
        for (int i = 0; i < PlaceOpinionMemory.MAX_ENTRIES + 5; i++) {
            memory.recordOutcome(new ChunkPos(i, 0).toLong(), 1f);
        }
        assertEquals(PlaceOpinionMemory.MAX_ENTRIES, memory.trackedPlaceCount());
        assertEquals(0f, memory.preference(new ChunkPos(0, 0)));
        assertTrue(memory.preference(new ChunkPos(PlaceOpinionMemory.MAX_ENTRIES + 4, 0)) > 0f);
    }

    @Test
    void clearResetsAllEntries() {
        PlaceOpinionMemory memory = new PlaceOpinionMemory();
        long key = new ChunkPos(8, 8).toLong();
        memory.recordOutcome(key, 20f);
        memory.clear();
        assertEquals(0, memory.trackedPlaceCount());
        assertEquals(0f, memory.preference(key));
    }
}
