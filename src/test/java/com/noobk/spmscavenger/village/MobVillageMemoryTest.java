package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** V1 — settlement identity, the RET-1 bound, and round-tripping. */
class MobVillageMemoryTest {

    private static ObservationQuality complete(int admitted) {
        return ObservationQuality.fullCoverage(admitted);
    }

    /** Villages far enough apart to never merge under any candidate identity radius. */
    private static BlockPos far(int index) {
        return new BlockPos(index * 500, 64, 0);
    }

    @Test
    void mustHappen_reObservingTheSameSettlementMergesRatherThanDuplicating() {
        MobVillageMemory memory = new MobVillageMemory();
        KnownVillage first = memory.remember(new BlockPos(0, 64, 0), 100L, complete(12));
        // 30 blocks apart: the same settlement seen from another side (identity radius is 48).
        KnownVillage again =
                memory.remember(new BlockPos(24, 64, 18), 200L, ObservationQuality.withCoverage(5, 10, 9));

        assertEquals(1, memory.size(), "two anchors inside the identity radius are one village");
        assertSame(first, again, "a less complete re-observation keeps the better-supported anchor");
        assertEquals(200L, again.lastSeenTick());
        assertEquals(100L, again.firstSeenTick());
    }

    /**
     * A glancing pass at the village edge sees few POIs and would produce a poor anchor. Recency
     * alone must not let it overwrite a well-supported one — that is how a good anchor decays into a
     * bad one over many visits, drifting out of agreement with {@code Raid.getCenter()}.
     */
    @Test
    void mustNotHappen_weakerObservationOverwritesAGoodAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos good = new BlockPos(0, 64, 0);
        memory.remember(good, 100L, complete(30));
        memory.remember(new BlockPos(30, 64, 0), 500L, ObservationQuality.withCoverage(2, 28, 2));

        assertEquals(good, memory.at(good).orElseThrow().anchor());
        assertEquals(30, memory.at(good).orElseThrow().poiCount());
    }

    @Test
    void mustHappen_strongerObservationReplacesTheAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(new BlockPos(0, 64, 0), 100L, ObservationQuality.withCoverage(4, 24, 4));
        BlockPos better = new BlockPos(24, 64, 16);
        memory.remember(better, 300L, complete(25));

        assertEquals(better, memory.at(better).orElseThrow().anchor());
        assertEquals(1, memory.size());
        assertEquals(100L, memory.at(better).orElseThrow().firstSeenTick(), "still the same settlement");
    }

    /**
     * Gate RET-1 — the bound. An exploring PlayerMob crosses villages indefinitely; without a cap
     * this list grows for the life of the save and no unit test would ever observe it.
     */
    @Test
    void mustNotHappen_villageListGrowsWithoutBound() {
        MobVillageMemory memory = new MobVillageMemory();
        for (int i = 0; i < MobVillageMemory.MAX_KNOWN_VILLAGES * 4; i++) {
            memory.remember(far(i), i, complete(5));
        }
        assertEquals(MobVillageMemory.MAX_KNOWN_VILLAGES, memory.size());
    }

    @Test
    void mustHappen_evictionTakesTheLeastRecentlySeen() {
        MobVillageMemory memory = new MobVillageMemory();
        for (int i = 0; i < MobVillageMemory.MAX_KNOWN_VILLAGES; i++) {
            memory.remember(far(i), 1000L + i, complete(5));
        }
        BlockPos stalest = far(0);
        assertTrue(memory.at(stalest).isPresent());

        memory.remember(far(999), 9999L, complete(5));
        assertFalse(memory.at(stalest).isPresent(), "the oldest sighting is the one that goes");
        assertTrue(memory.at(far(999)).isPresent());
    }

    /**
     * Home is the one entry whose loss is not recoverable by walking past again — rediscovery
     * restores the settlement but not the designation.
     */
    @Test
    void mustNotHappen_homeVillageIsEvicted() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = far(0);
        memory.remember(home, 1L, complete(5));
        assertTrue(memory.designateHome(home));

        for (int i = 1; i < MobVillageMemory.MAX_KNOWN_VILLAGES * 3; i++) {
            memory.remember(far(i), 1000L + i, complete(5));
        }

        assertTrue(memory.at(home).isPresent(), "home survives despite being the stalest entry");
        assertTrue(memory.home().isPresent());
        assertEquals(home, memory.home().orElseThrow().anchor());
        assertEquals(MobVillageMemory.MAX_KNOWN_VILLAGES, memory.size(), "the bound still holds");
    }

    /** "Is this my home" must have one answer; D-VR-010 will abandon work based on it. */
    @Test
    void mustNotHappen_twoHomesAtOnce() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        memory.remember(far(1), 2L, complete(5));
        memory.designateHome(far(0));
        memory.designateHome(far(1));

        assertTrue(memory.homeAnchor().isPresent(), "home has one independent optional slot");
        assertEquals(far(1), memory.home().orElseThrow().anchor());
    }

    @Test
    void mustNotHappen_designatingAnUnknownAnchorAsHome() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        assertFalse(memory.designateHome(far(7)), "cannot make a home of a village never seen");
        assertTrue(memory.home().isEmpty());
    }

    @Test
    void mustHappen_roundTripsThroughNbt() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 10L, complete(7));
        memory.remember(far(1), 20L, complete(3));
        memory.designateHome(far(1));

        MobVillageMemory reloaded = MobVillageMemory.load(memory.save());

        assertEquals(2, reloaded.size());
        assertEquals(far(1), reloaded.home().orElseThrow().anchor());
        assertEquals(7, reloaded.at(far(0)).orElseThrow().poiCount());
        assertEquals(10L, reloaded.at(far(0)).orElseThrow().firstSeenTick());
    }

    /** A corrupt or hand-edited save must not resurrect a village at the world origin. */
    @Test
    void mustNotHappen_unreadableRowLoadsAsAVillage() {
        CompoundTag noAnchor = new CompoundTag();
        noAnchor.putString("tier", "HOME_VILLAGE");
        assertEquals(null, KnownVillage.load(noAnchor));

        KnownVillage good = KnownVillage.discovered(far(0), 5L, complete(3));
        CompoundTag unknownTier = good.save();
        unknownTier.putString("tier", "MARKET_TOWN");
        assertNotNull(KnownVillage.load(unknownTier),
                "obsolete role text must not invalidate factual settlement evidence");
    }

    @Test
    void mustNotHappen_twoHomesSurviveALoad() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        memory.remember(far(1), 2L, complete(5));
        CompoundTag tag = memory.save();
        // Forge a corrupt save with two homes.
        tag.getList("villages", 10).getCompound(0).putString("tier", "HOME_VILLAGE");
        tag.getList("villages", 10).getCompound(1).putString("tier", "HOME_VILLAGE");

        MobVillageMemory reloaded = MobVillageMemory.load(tag);
        assertEquals(far(0), reloaded.homeAnchor().orElseThrow(),
                "corrupt legacy duplicates choose the first valid list row");
    }

    @Test
    void mustHappen_discoveryDoesNotManufactureHomeOrAnotherRole() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(far(0), 1L, complete(5));
        assertTrue(memory.home().isEmpty());
        assertFalse(memory.save().contains("homeAnchor"));
    }
}
