package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Pure SCR-2 shortlist, semantic-tier, and ordering policy. */
final class ShelterSelectionPolicy {

    static final int BUCKET_SIZE = 2;
    static final int DISTANCE_BANDS = 4;
    static final int GENERIC_PER_BAND = 6;
    static final int MAX_GENERIC_CANDIDATES = DISTANCE_BANDS * GENERIC_PER_BAND;
    static final int MAX_BED_CANDIDATES = 4;
    static final int MAX_DOOR_ASSOCIATED_CANDIDATES = 4;
    static final int MAX_SHORTLIST = MAX_GENERIC_CANDIDATES + MAX_BED_CANDIDATES;
    static final int MAX_PATH_PROBES = 4;
    static final int BOUNDARY_PROBE_DISTANCE = 5;
    static final int DOOR_PROBE_DISTANCE = 4;

    enum Tier {
        EXPOSED,
        PORCH_OVERHANG,
        DEEPLY_COVERED,
        INTERIOR_ROOM,
        USABLE_BED
    }

    record RawCandidate(
            BlockPos standPos,
            @Nullable BlockPos bedPos,
            int immediateSolidNeighbours,
            int blockLight,
            double distance) {

        RawCandidate {
            standPos = standPos.immutable();
            bedPos = bedPos == null ? null : bedPos.immutable();
        }

        boolean bed() {
            return bedPos != null;
        }
    }

    record Evidence(
            int horizontalBoundaries,
            int structuralRoofCoverage,
            int foliageRoofCoverage,
            int doorClearance) {
        Evidence {
            if (horizontalBoundaries < 0 || horizontalBoundaries > 4) {
                throw new IllegalArgumentException("horizontalBoundaries must be 0..4");
            }
            if (structuralRoofCoverage < 0 || structuralRoofCoverage > 5) {
                throw new IllegalArgumentException("structuralRoofCoverage must be 0..5");
            }
            if (foliageRoofCoverage < 0 || foliageRoofCoverage > 5
                    || structuralRoofCoverage + foliageRoofCoverage > 5) {
                throw new IllegalArgumentException("combined roof coverage must be 0..5");
            }
            if (doorClearance < 0 || doorClearance > DOOR_PROBE_DISTANCE + 1) {
                throw new IllegalArgumentException("doorClearance must be 0..5");
            }
        }

        /** Compatibility seam: older tests supplied total roof cover, all structural. */
        Evidence(int horizontalBoundaries, int roofCoverage, int doorClearance) {
            this(horizontalBoundaries, roofCoverage, 0, doorClearance);
        }

        int roofCoverage() {
            return structuralRoofCoverage + foliageRoofCoverage;
        }
    }

    record RankedCandidate(RawCandidate raw, Tier tier, Evidence evidence) {
    }

    static final class PathProbeBudget {
        private int used;

        boolean tryAcquire() {
            if (used >= MAX_PATH_PROBES) {
                return false;
            }
            used++;
            return true;
        }

        int used() {
            return used;
        }

        boolean exhausted() {
            return used >= MAX_PATH_PROBES;
        }
    }

    private record BucketKey(int x, int y, int z) {
        static BucketKey of(BlockPos pos) {
            return new BucketKey(
                    Math.floorDiv(pos.getX(), BUCKET_SIZE),
                    Math.floorDiv(pos.getY(), BUCKET_SIZE),
                    Math.floorDiv(pos.getZ(), BUCKET_SIZE));
        }
    }

    private record BucketRepresentatives(RawCandidate enclosed, RawCandidate open) {
        BucketRepresentatives add(RawCandidate candidate) {
            RawCandidate nextEnclosed = enclosed == null ? candidate : cheaper(enclosed, candidate);
            RawCandidate nextOpen = open == null || OPEN_ORDER.compare(candidate, open) < 0
                    ? candidate : open;
            return new BucketRepresentatives(nextEnclosed, nextOpen);
        }
    }

    private static final Comparator<RawCandidate> CHEAP_ORDER = Comparator
            .comparingInt(RawCandidate::immediateSolidNeighbours).reversed()
            .thenComparing(Comparator.comparingInt(RawCandidate::blockLight).reversed())
            .thenComparingDouble(RawCandidate::distance)
            .thenComparingInt(candidate -> candidate.standPos().getX())
            .thenComparingInt(candidate -> candidate.standPos().getY())
            .thenComparingInt(candidate -> candidate.standPos().getZ());

    /** Preserve room-center/wide-cave representatives that immediate-wall scoring underrates. */
    private static final Comparator<RawCandidate> OPEN_ORDER = Comparator
            .comparingInt(RawCandidate::immediateSolidNeighbours)
            .thenComparing(Comparator.comparingInt(RawCandidate::blockLight).reversed())
            .thenComparingDouble(RawCandidate::distance)
            .thenComparingInt(candidate -> candidate.standPos().getX())
            .thenComparingInt(candidate -> candidate.standPos().getY())
            .thenComparingInt(candidate -> candidate.standPos().getZ());

    private static final Comparator<RankedCandidate> SEMANTIC_ORDER = Comparator
            .comparingInt((RankedCandidate candidate) -> candidate.tier().ordinal()).reversed()
            .thenComparing(Comparator.comparingInt(
                    ShelterSelectionPolicy::genericDoorClearance).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RankedCandidate candidate) -> candidate.evidence().horizontalBoundaries()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RankedCandidate candidate) -> candidate.evidence().structuralRoofCoverage()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RankedCandidate candidate) -> candidate.evidence().roofCoverage()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (RankedCandidate candidate) -> candidate.raw().blockLight()).reversed())
            .thenComparingDouble(candidate -> candidate.raw().distance())
            .thenComparingInt(candidate -> candidate.raw().standPos().getX())
            .thenComparingInt(candidate -> candidate.raw().standPos().getY())
            .thenComparingInt(candidate -> candidate.raw().standPos().getZ());

    private ShelterSelectionPolicy() {
    }

    static Tier classify(boolean usableBed, Evidence evidence) {
        if (usableBed) {
            return Tier.USABLE_BED;
        }
        if (evidence.doorClearance() <= 1) {
            return evidence.roofCoverage() >= 1 ? Tier.PORCH_OVERHANG : Tier.EXPOSED;
        }
        if (evidence.horizontalBoundaries() >= 3 && evidence.structuralRoofCoverage() >= 3) {
            return Tier.INTERIOR_ROOM;
        }
        if (evidence.horizontalBoundaries() >= 2 && evidence.structuralRoofCoverage() >= 4) {
            return Tier.DEEPLY_COVERED;
        }
        if (evidence.roofCoverage() >= 1) {
            return Tier.PORCH_OVERHANG;
        }
        return Tier.EXPOSED;
    }

    /**
     * One cheap winner per 2x2x2 bucket, then a fixed share from each distance band. Beds are
     * canonical-position deduplicated and cannot consume the generic capacity.
     */
    static List<RawCandidate> diverseShortlist(
            Collection<RawCandidate> candidates, double maxDistance) {
        return diverseShortlist(candidates, maxDistance, List.of());
    }

    static List<RawCandidate> diverseShortlist(
            Collection<RawCandidate> candidates,
            double maxDistance,
            Collection<BlockPos> doorPositions) {
        Map<BlockPos, RawCandidate> beds = new LinkedHashMap<>();
        Map<BucketKey, BucketRepresentatives> buckets = new HashMap<>();
        for (RawCandidate candidate : candidates) {
            if (candidate.bed()) {
                beds.merge(candidate.bedPos(), candidate, ShelterSelectionPolicy::cheaper);
            } else {
                buckets.compute(BucketKey.of(candidate.standPos()), (ignored, representatives) ->
                        (representatives == null ? new BucketRepresentatives(null, null) : representatives)
                                .add(candidate));
            }
        }

        List<RawCandidate> result = beds.values().stream()
                .sorted(Comparator.comparingDouble(RawCandidate::distance)
                        .thenComparing(CHEAP_ORDER))
                .limit(MAX_BED_CANDIDATES)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        int selectedBedCount = result.size();

        List<List<RawCandidate>> bands = new ArrayList<>(DISTANCE_BANDS);
        for (int i = 0; i < DISTANCE_BANDS; i++) {
            bands.add(new ArrayList<>());
        }
        Map<BlockPos, RawCandidate> representatives = new LinkedHashMap<>();
        for (BucketRepresentatives bucket : buckets.values()) {
            representatives.put(bucket.enclosed().standPos(), bucket.enclosed());
            representatives.put(bucket.open().standPos(), bucket.open());
        }
        for (RawCandidate candidate : representatives.values()) {
            bands.get(distanceBand(candidate.distance(), maxDistance)).add(candidate);
        }

        List<RawCandidate> leftovers = new ArrayList<>();
        for (List<RawCandidate> band : bands) {
            band.sort(CHEAP_ORDER);
            int enclosedTake = Math.min(GENERIC_PER_BAND / 2, band.size());
            List<RawCandidate> selected = new ArrayList<>(band.subList(0, enclosedTake));
            band.stream()
                    .filter(candidate -> !selected.contains(candidate))
                    .sorted(OPEN_ORDER)
                    .limit(GENERIC_PER_BAND - selected.size())
                    .forEach(selected::add);
            result.addAll(selected);
            band.stream().filter(candidate -> !selected.contains(candidate)).forEach(leftovers::add);
        }
        leftovers.sort(CHEAP_ORDER);
        int genericCount = result.size() - selectedBedCount;
        int fill = Math.min(MAX_GENERIC_CANDIDATES - genericCount, leftovers.size());
        result.addAll(leftovers.subList(0, fill));
        List<RawCandidate> doorAssociated = doorAssociatedCandidates(candidates, doorPositions);
        Set<BlockPos> present = new HashSet<>();
        result.forEach(candidate -> present.add(candidate.standPos()));
        for (RawCandidate doorCandidate : doorAssociated) {
            if (!present.add(doorCandidate.standPos())) {
                continue;
            }
            while (result.size() >= MAX_SHORTLIST) {
                int removable = lastRemovableGenericIndex(result, doorAssociated);
                if (removable < 0) {
                    break;
                }
                present.remove(result.remove(removable).standPos());
            }
            if (result.size() < MAX_SHORTLIST) {
                result.add(doorCandidate);
            }
        }
        if (result.size() > MAX_SHORTLIST) {
            return List.copyOf(result.subList(0, MAX_SHORTLIST));
        }
        return List.copyOf(result);
    }

    private static List<RawCandidate> doorAssociatedCandidates(
            Collection<RawCandidate> candidates, Collection<BlockPos> doorPositions) {
        if (doorPositions.isEmpty()) {
            return List.of();
        }
        Map<BucketKey, BucketRepresentatives> buckets = new HashMap<>();
        for (RawCandidate candidate : candidates) {
            if (candidate.bed() || doorAssociationDistance(candidate.standPos(), doorPositions) < 2
                    || doorAssociationDistance(candidate.standPos(), doorPositions) > DOOR_PROBE_DISTANCE) {
                continue;
            }
            buckets.compute(BucketKey.of(candidate.standPos()), (ignored, representatives) ->
                    (representatives == null ? new BucketRepresentatives(null, null) : representatives)
                            .add(candidate));
        }
        Map<BlockPos, RawCandidate> representatives = new LinkedHashMap<>();
        for (BucketRepresentatives bucket : buckets.values()) {
            representatives.put(bucket.enclosed().standPos(), bucket.enclosed());
            representatives.put(bucket.open().standPos(), bucket.open());
        }
        return representatives.values().stream()
                .sorted(Comparator
                        .comparingInt((RawCandidate candidate) ->
                                doorAssociationDistance(candidate.standPos(), doorPositions)).reversed()
                        .thenComparing(CHEAP_ORDER))
                .limit(MAX_DOOR_ASSOCIATED_CANDIDATES)
                .toList();
    }

    private static int lastRemovableGenericIndex(
            List<RawCandidate> result, List<RawCandidate> doorAssociated) {
        Set<BlockPos> protectedPositions = new HashSet<>();
        doorAssociated.forEach(candidate -> protectedPositions.add(candidate.standPos()));
        for (int i = result.size() - 1; i >= 0; i--) {
            RawCandidate candidate = result.get(i);
            if (!candidate.bed() && !protectedPositions.contains(candidate.standPos())) {
                return i;
            }
        }
        return -1;
    }

    private static int doorAssociationDistance(BlockPos pos, Collection<BlockPos> doors) {
        int best = Integer.MAX_VALUE;
        for (BlockPos door : doors) {
            if (Math.abs(pos.getY() - door.getY()) > 1) {
                continue;
            }
            best = Math.min(best,
                    Math.abs(pos.getX() - door.getX()) + Math.abs(pos.getZ() - door.getZ()));
        }
        return best;
    }

    static List<RankedCandidate> rank(Collection<RankedCandidate> candidates) {
        return candidates.stream().sorted(SEMANTIC_ORDER).toList();
    }

    static boolean routeWithinExposureBudget(
            Iterable<Boolean> structurallyProtectedNodes, int maxExposedNodes) {
        int exposed = 0;
        for (boolean structurallyProtected : structurallyProtectedNodes) {
            if (!structurallyProtected && ++exposed > maxExposedNodes) {
                return false;
            }
        }
        return true;
    }

    static boolean arrivedAtStandingSite(BlockPos current, BlockPos destination) {
        return current.equals(destination);
    }

    private static int genericDoorClearance(RankedCandidate candidate) {
        return candidate.raw().bed() ? 0 : candidate.evidence().doorClearance();
    }

    private static RawCandidate cheaper(RawCandidate left, RawCandidate right) {
        return CHEAP_ORDER.compare(left, right) <= 0 ? left : right;
    }

    private static int distanceBand(double distance, double maxDistance) {
        double width = Math.max(1.0, maxDistance / DISTANCE_BANDS);
        return Math.min(DISTANCE_BANDS - 1, Math.max(0, (int) Math.floor(distance / width)));
    }
}
