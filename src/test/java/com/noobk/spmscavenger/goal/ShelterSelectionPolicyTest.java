package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterSelectionPolicyTest {

    @Test
    void semanticTierAlwaysBeatsDistanceAndQualityArithmetic() {
        ShelterSelectionPolicy.RankedCandidate farInterior = ranked(
                25, ShelterSelectionPolicy.Tier.INTERIOR_ROOM, 3, 3, 5, 0);
        ShelterSelectionPolicy.RankedCandidate nearPorch = ranked(
                4, ShelterSelectionPolicy.Tier.PORCH_OVERHANG, 1, 5, 1, 15);

        List<ShelterSelectionPolicy.RankedCandidate> ranked =
                ShelterSelectionPolicy.rank(List.of(nearPorch, farInterior));

        assertEquals(farInterior, ranked.getFirst());
    }

    @Test
    void usableBedRanksAboveInteriorAndClassificationIsExhaustive() {
        assertEquals(ShelterSelectionPolicy.Tier.USABLE_BED,
                ShelterSelectionPolicy.classify(true, new ShelterSelectionPolicy.Evidence(0, 0, 0)));
        assertEquals(ShelterSelectionPolicy.Tier.INTERIOR_ROOM,
                ShelterSelectionPolicy.classify(false, new ShelterSelectionPolicy.Evidence(3, 3, 5)));
        assertEquals(ShelterSelectionPolicy.Tier.DEEPLY_COVERED,
                ShelterSelectionPolicy.classify(false, new ShelterSelectionPolicy.Evidence(2, 4, 5)));
        assertEquals(ShelterSelectionPolicy.Tier.PORCH_OVERHANG,
                ShelterSelectionPolicy.classify(false, new ShelterSelectionPolicy.Evidence(1, 1, 5)));
        assertEquals(ShelterSelectionPolicy.Tier.EXPOSED,
                ShelterSelectionPolicy.classify(false, new ShelterSelectionPolicy.Evidence(0, 0, 5)));
    }

    @Test
    void doorwayAdjacencyCannotMasqueradeAsInterior() {
        assertEquals(ShelterSelectionPolicy.Tier.PORCH_OVERHANG,
                ShelterSelectionPolicy.classify(
                        false, new ShelterSelectionPolicy.Evidence(4, 5, 1)));
    }

    @Test
    void deeperInteriorWinsWithinTierAndArrivalRequiresReservedBlock() {
        ShelterSelectionPolicy.RankedCandidate threshold = ranked(
                3, ShelterSelectionPolicy.Tier.INTERIOR_ROOM, 4, 5, 2, 15);
        ShelterSelectionPolicy.RankedCandidate roomCenter = ranked(
                6, ShelterSelectionPolicy.Tier.INTERIOR_ROOM, 4, 5, 4, 8);

        assertEquals(roomCenter,
                ShelterSelectionPolicy.rank(List.of(threshold, roomCenter)).getFirst());
        assertFalse(ShelterSelectionPolicy.arrivedAtStandingSite(
                new BlockPos(5, 64, 0), new BlockPos(7, 64, 0)));
        assertTrue(ShelterSelectionPolicy.arrivedAtStandingSite(
                new BlockPos(7, 64, 0), new BlockPos(7, 64, 0)));
    }

    @Test
    void shortlistIsBoundedSpatiallyDeduplicatedAndKeepsDistanceBands() {
        List<ShelterSelectionPolicy.RawCandidate> candidates = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 12; z++) {
                candidates.add(raw(x, 64, z, false, 2, 8, Math.hypot(x, z)));
            }
        }
        // A farther semantic opportunity must survive the cheap stage even when near porch cells
        // are numerous. It occupies the outer distance band and a distinct spatial bucket.
        ShelterSelectionPolicy.RawCandidate far = raw(15, 64, 0, false, 0, 0, 15);
        candidates.add(far);

        List<ShelterSelectionPolicy.RawCandidate> shortlist =
                ShelterSelectionPolicy.diverseShortlist(candidates, 16);

        assertTrue(shortlist.size() <= ShelterSelectionPolicy.MAX_SHORTLIST);
        assertTrue(shortlist.contains(far));
        long firstBucket = shortlist.stream()
                .filter(candidate -> candidate.standPos().getX() < 2
                        && candidate.standPos().getZ() < 2)
                .count();
        assertEquals(1, firstBucket);
    }

    @Test
    void bedCapacityDoesNotReduceGenericCapacity() {
        List<ShelterSelectionPolicy.RawCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < ShelterSelectionPolicy.MAX_GENERIC_CANDIDATES + 8; i++) {
            candidates.add(raw(i * 2, 64, 0, false, 1, 1, i));
        }
        candidates.add(raw(0, 64, 4, true, 0, 0, 4));

        List<ShelterSelectionPolicy.RawCandidate> shortlist =
                ShelterSelectionPolicy.diverseShortlist(candidates, 64);

        assertEquals(ShelterSelectionPolicy.MAX_GENERIC_CANDIDATES + 1, shortlist.size());
        assertEquals(ShelterSelectionPolicy.MAX_GENERIC_CANDIDATES,
                shortlist.stream().filter(candidate -> !candidate.bed()).count());
    }

    @Test
    void pathProbeBudgetAllowsExactlyFourProbes() {
        ShelterSelectionPolicy.PathProbeBudget budget =
                new ShelterSelectionPolicy.PathProbeBudget();

        for (int i = 0; i < ShelterSelectionPolicy.MAX_PATH_PROBES; i++) {
            assertTrue(budget.tryAcquire());
        }
        assertFalse(budget.tryAcquire());
        assertEquals(ShelterSelectionPolicy.MAX_PATH_PROBES, budget.used());
    }

    private static ShelterSelectionPolicy.RankedCandidate ranked(
            double distance,
            ShelterSelectionPolicy.Tier tier,
            int boundaries,
            int roof,
            int doorClearance,
            int light) {
        return new ShelterSelectionPolicy.RankedCandidate(
                raw((int) distance, 64, 0, false, 0, light, distance),
                tier,
                new ShelterSelectionPolicy.Evidence(boundaries, roof, doorClearance));
    }

    private static ShelterSelectionPolicy.RawCandidate raw(
            int x, int y, int z, boolean bed, int neighbours, int light, double distance) {
        BlockPos stand = new BlockPos(x, y, z);
        return new ShelterSelectionPolicy.RawCandidate(
                stand,
                bed ? stand : null,
                neighbours,
                light,
                distance);
    }
}
