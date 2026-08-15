package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * V1-R1 — the three corrections found in review of V1.
 *
 * <p>Each was a defect the original tests were structurally incapable of catching, and one was a
 * defect the original tests actively <em>enforced</em>.
 */
class VillageLifecycleAndIdentityTest {

    private static ObservationQuality complete(int admitted) {
        return ObservationQuality.fullCoverage(admitted);
    }

    private static ObservationQuality partial(int admitted, int loaded, int total) {
        return ObservationQuality.withCoverage(loaded, total, admitted);
    }

    // ------------------------------------------------------------------ P1a: identity vs raid

    /**
     * The failure the collapsed radius could not represent: a home and a trading post 85 blocks
     * apart. Under the old 96-block identity radius these were one {@code KnownVillage} and could not
     * hold two tiers at once.
     */
    @Test
    void mustHappen_twoSettlements85BlocksApartStaySeparate() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        BlockPos tradingPost = new BlockPos(85, 64, 0);

        memory.remember(home, 100L, complete(20));
        memory.remember(tradingPost, 110L, complete(14));

        assertEquals(2, memory.size(), "85 blocks apart is two places, not one");
        assertTrue(memory.designateHome(home));
        assertEquals(home, memory.home().orElseThrow().anchor());
        assertNotEquals(memory.at(home).orElseThrow(), memory.at(tradingPost).orElseThrow());
    }

    /**
     * …and vanilla still gets its answer. One raid legitimately covers both, which is now
     * representable instead of being resolved by accident at merge time.
     */
    @Test
    void mustHappen_oneRaidCanCoverBothRememberedVillages() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        BlockPos tradingPost = new BlockPos(85, 64, 0);
        memory.remember(home, 100L, complete(20));
        memory.remember(tradingPost, 110L, complete(14));

        List<KnownVillage> covered =
                RaidAssociationPolicy.associatedVillages(memory.villages(), new BlockPos(40, 64, 0));
        assertEquals(2, covered.size(), "the raid radius is unchanged; only identity narrowed");
    }

    /** The compatibility half must not drift — D-VR-019 and D-VR-010 both depend on it. */
    @Test
    void mustHappen_raidRadiusStillMatchesVanilla() {
        assertEquals(9216, RaidAssociationPolicy.RAID_ASSOCIATION_RADIUS_SQR,
                "ServerLevel#getRaidAt passes 9216 to getNearbyRaid");
        BlockPos anchor = new BlockPos(0, 64, 0);
        assertTrue(RaidAssociationPolicy.associated(anchor, new BlockPos(95, 64, 0)));
        assertFalse(RaidAssociationPolicy.associated(anchor, new BlockPos(97, 64, 0)));
    }

    /** Identity must stay strictly tighter than raid association, or the split is decorative. */
    @Test
    void mustHappen_identityIsTighterThanRaidAssociation() {
        assertTrue(VillageIdentityPolicy.SAME_SETTLEMENT_RADIUS_SQR
                        < RaidAssociationPolicy.RAID_ASSOCIATION_RADIUS_SQR,
                "cognitive identity must be narrower than vanilla's raid neighbourhood");
        assertTrue(VillageIdentityPolicy.SAME_SETTLEMENT_RADIUS_SQR
                        < VillagePerception.VILLAGE_QUERY_RADIUS * VillagePerception.VILLAGE_QUERY_RADIUS,
                "and below the query radius, so two views of one settlement converge rather than fork");
    }

    /** A settlement observed from two sides still merges — the tighter radius must not fork it. */
    @Test
    void mustHappen_oneSettlementSeenFromTwoSidesStillMerges() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(new BlockPos(0, 64, 0), 100L, complete(18));
        memory.remember(new BlockPos(20, 64, 15), 200L, complete(18));
        assertEquals(1, memory.size(), "25 blocks apart is the same village seen from another side");
    }

    // ------------------------------------------------------------------ P1b: quality, not quantity

    /**
     * The village loses buildings. Under {@code newCount > oldCount} the anchor froze forever, and
     * silently drifted out of agreement with {@code Raid.getCenter()} — D-VR-019's failure, reached
     * from the opposite direction.
     */
    @Test
    void mustHappen_shrunkenVillageUpdatesItsAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos original = new BlockPos(0, 64, 0);
        memory.remember(original, 100L, complete(20));

        BlockPos afterDestruction = new BlockPos(12, 64, 8);
        memory.remember(afterDestruction, 5_000L, complete(16));

        assertEquals(afterDestruction, memory.at(afterDestruction).orElseThrow().anchor(),
                "16 POIs seen completely is not a worse view than 20 seen completely");
        assertEquals(16, memory.at(afterDestruction).orElseThrow().poiCount());
    }

    /** The village is rebuilt in place: same count, different positions. {@code 20 <= 20} froze it. */
    @Test
    void mustHappen_rebuiltVillageUpdatesItsAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(new BlockPos(0, 64, 0), 100L, complete(20));
        BlockPos moved = new BlockPos(15, 64, 15);
        memory.remember(moved, 9_000L, complete(20));
        assertEquals(moved, memory.at(moved).orElseThrow().anchor());
    }

    /** The protection the original rule was written for must survive the repair. */
    @Test
    void mustNotHappen_edgeGlanceDegradesAGoodAnchor() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos good = new BlockPos(0, 64, 0);
        memory.remember(good, 100L, complete(20));

        // Standing at the rim: three POIs admitted, twenty-five refused by the boundary.
        memory.remember(new BlockPos(30, 64, 20), 50_000L, partial(3, 2, 20));

        assertEquals(good, memory.at(good).orElseThrow().anchor(),
                "a 3/28 view must never overwrite a complete one, however recent");
        assertEquals(50_000L, memory.at(good).orElseThrow().lastSeenTick(),
                "but the sighting still counts for staleness");
    }

    /** A more complete view wins even when it admits fewer POIs than the stored one. */
    @Test
    void mustHappen_moreCompleteViewWinsOverLargerCount() {
        MobVillageMemory memory = new MobVillageMemory();
        memory.remember(new BlockPos(0, 64, 0), 100L, partial(18, 9, 15));
        BlockPos better = new BlockPos(10, 64, 10);
        memory.remember(better, 200L, complete(9));
        assertEquals(better, memory.at(better).orElseThrow().anchor());
    }

    @Test
    void mustHappen_coverageMathIsHonest() {
        assertEquals(1f, complete(10).coverageRatio());
        assertEquals(0f, ObservationQuality.withCoverage(0, 7, 0).coverageRatio());
        assertEquals(0.5f, partial(5, 5, 10).coverageRatio());
        assertFalse(ObservationQuality.withCoverage(0, 0, 0).isComplete(),
                "zero footprint is not a complete view");
        assertTrue(complete(1).isComplete());
    }

    /** The perception layer must hand the memory layer coverage, not hidden POI counts (V1-R4). */
    @Test
    void mustHappen_observationCarriesCoverage() {
        PerceptionCoverage partial = new PerceptionCoverage(4, 11);
        VillagePerception.Observation observation = new VillagePerception.Observation(
                new BlockPos(0, 64, 0), 4, partial);
        assertTrue(observation.partiallyPerceived());
        ObservationQuality quality = ObservationQuality.of(observation.coverage(), observation.admittedPoiCount());
        assertEquals(4, quality.loadedColumns());
        assertEquals(11, quality.totalColumns());
        assertFalse(quality.isComplete());
    }

    // ------------------------------------------------------------------ P0: NBT migration

    /**
     * A save written before V1-R1 has {@code poiCount} and no {@code quality}. It must load as a
     * complete observation of that size — treating it as unusable would let the first partial glance
     * after the update overwrite a good anchor, which is the defect the rule exists to prevent.
     */
    @Test
    void mustHappen_preR1WithheldLoadsAsOptimisticFullCoverage() {
        net.minecraft.nbt.CompoundTag legacyQuality = new net.minecraft.nbt.CompoundTag();
        legacyQuality.putInt("admitted", 12);
        legacyQuality.putInt("withheld", 40);

        ObservationQuality loaded = ObservationQuality.load(legacyQuality);
        assertTrue(loaded.isComplete(), "withheld must not be reinterpreted");
        assertEquals(12, loaded.admitted());
    }

    @Test
    void mustHappen_preUpgradeRowsLoadAsCompleteObservations() {
        net.minecraft.nbt.CompoundTag legacy = KnownVillage.discovered(
                new BlockPos(0, 64, 0), 10L, complete(12)).save();
        legacy.remove("quality");
        legacy.putInt("poiCount", 12);

        KnownVillage loaded = KnownVillage.load(legacy);
        assertEquals(12, loaded.poiCount());
        assertTrue(loaded.quality().isComplete(), "optimistic, and stated as such");
    }

    @Test
    void mustHappen_qualityRoundTripsThroughNbt() {
        KnownVillage village = KnownVillage.discovered(new BlockPos(0, 64, 0), 10L, partial(6, 6, 15));
        KnownVillage loaded = KnownVillage.load(village.save());
        assertEquals(6, loaded.quality().admitted());
        assertEquals(6, loaded.quality().loadedColumns());
        assertEquals(15, loaded.quality().totalColumns());
    }
}
