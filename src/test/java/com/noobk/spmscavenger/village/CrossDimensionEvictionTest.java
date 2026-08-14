package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * V1-R3 — permanent removal must clear every dimension, not the one the mob happened to die in.
 *
 * <p>Memory is per-dimension; a mob is not. The two are reconciled only by the global sweep.
 */
class CrossDimensionEvictionTest {

    private static ObservationQuality complete(int admitted) {
        return new ObservationQuality(admitted, 0);
    }

    /**
     * The exact shipped sequence, replayed against two stores.
     *
     * <p>Villages are an Overworld feature; PlayerMobs die in the Nether. Under the per-level
     * eviction this was not a rare edge case but the <b>common</b> path, and every such death left a
     * permanent Overworld entry.
     */
    @Test
    void mustHappen_deathInTheNetherClearsOverworldMemory() {
        UUID mob = UUID.randomUUID();
        VillageMemorySavedData overworld = new VillageMemorySavedData();
        VillageMemorySavedData nether = new VillageMemorySavedData();

        overworld.memoryOf(mob).remember(new BlockPos(0, 64, 0), 100L, complete(20));
        assertEquals(1, overworld.trackedMobCount());
        assertEquals(0, nether.trackedMobCount(), "the mob never saw a village in the Nether");

        // Dies in the Nether. The old code called forget() on the Nether store only.
        assertFalse(nether.forget(mob), "per-level eviction finds nothing to remove — silently");
        assertEquals(1, overworld.trackedMobCount(),
                "…and this is the leak: owner permanently gone, entry immortal");

        // V1-R3: the global sweep.
        assertEquals(1, VillageMemorySavedData.forgetIn(List.of(overworld, nether), mob),
                "one dimension actually held memory");
        assertEquals(0, overworld.trackedMobCount());
    }

    @Test
    void mustHappen_memoryInSeveralDimensionsIsAllCleared() {
        UUID mob = UUID.randomUUID();
        VillageMemorySavedData overworld = new VillageMemorySavedData();
        VillageMemorySavedData other = new VillageMemorySavedData();
        overworld.memoryOf(mob).remember(new BlockPos(0, 64, 0), 100L, complete(12));
        other.memoryOf(mob).remember(new BlockPos(500, 64, 0), 200L, complete(8));

        assertEquals(2, VillageMemorySavedData.forgetIn(List.of(overworld, other), mob));
        assertEquals(0, overworld.trackedMobCount());
        assertEquals(0, other.trackedMobCount());
    }

    /** Other mobs' memory in the swept dimensions must survive untouched. */
    @Test
    void mustNotHappen_sweepingOneMobClearsAnother() {
        UUID dead = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        VillageMemorySavedData overworld = new VillageMemorySavedData();
        overworld.memoryOf(dead).remember(new BlockPos(0, 64, 0), 100L, complete(10));
        overworld.memoryOf(alive).remember(new BlockPos(0, 64, 0), 100L, complete(10));

        VillageMemorySavedData.forgetIn(List.of(overworld), dead);

        assertEquals(1, overworld.trackedMobCount());
        assertTrue(overworld.peek(alive).isPresent());
        assertTrue(overworld.peek(dead).isEmpty());
    }

    /** A dimension holding nothing for this mob is not an error, and must not be counted. */
    @Test
    void mustHappen_sweepIsSilentWhereThereIsNothingToClear() {
        UUID mob = UUID.randomUUID();
        assertEquals(0, VillageMemorySavedData.forgetIn(
                List.of(new VillageMemorySavedData(), new VillageMemorySavedData()), mob));
        assertEquals(0, VillageMemorySavedData.forgetIn(List.of(), mob));
    }

    /**
     * The other half of the contract, restated here because it is what makes the global sweep
     * necessary: a dimension change must <b>not</b> delete, and the UUID survives it, so the memory
     * remains correctly keyed for a mob that comes back.
     */
    @Test
    void mustNotHappen_dimensionChangeDeletesMemory() {
        UUID mob = UUID.randomUUID();
        VillageMemorySavedData overworld = new VillageMemorySavedData();
        overworld.memoryOf(mob).remember(new BlockPos(0, 64, 0), 100L, complete(15));

        // CHANGED_DIMENSION -> shouldDestroy() is false -> no eviction call happens at all.
        assertEquals(1, overworld.trackedMobCount());
        assertTrue(overworld.peek(mob).orElseThrow().at(new BlockPos(0, 64, 0)).isPresent(),
                "the returning mob finds its village again, under the same UUID");
    }
}
