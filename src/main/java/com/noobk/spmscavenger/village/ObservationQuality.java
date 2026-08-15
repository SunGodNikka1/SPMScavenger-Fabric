package com.noobk.spmscavenger.village;

import net.minecraft.nbt.CompoundTag;

/**
 * V1-R4 — observation quality for anchor replacement.
 *
 * <p>Supersede ordering uses {@link PerceptionCoverage} only. {@code admitted} is the settlement's
 * perceived POI count at observation time — diagnostics and {@link KnownVillage#poiCount()}, not
 * view quality.
 */
public record ObservationQuality(PerceptionCoverage coverage, int admitted) {

    public ObservationQuality {
        if (admitted < 0) {
            throw new IllegalArgumentException("admitted must be non-negative: " + admitted);
        }
        coverage = coverage == null ? PerceptionCoverage.optimisticFull() : coverage;
    }

    public static ObservationQuality of(PerceptionCoverage coverage, int admitted) {
        return new ObservationQuality(coverage, admitted);
    }

    /** Full coverage for unit tests and optimistic migration rows. */
    public static ObservationQuality fullCoverage(int admitted) {
        return new ObservationQuality(PerceptionCoverage.optimisticFull(), admitted);
    }

    public static ObservationQuality withCoverage(int loadedColumns, int totalColumns, int admitted) {
        return new ObservationQuality(new PerceptionCoverage(loadedColumns, totalColumns), admitted);
    }

    public int loadedColumns() {
        return coverage.loadedColumns();
    }

    public int totalColumns() {
        return coverage.totalColumns();
    }

    public boolean isComplete() {
        return coverage.isFull();
    }

    /** Derived view — not used for supersede decisions. */
    public float coverageRatio() {
        return coverage.ratio();
    }

    public boolean supersedes(ObservationQuality stored, long newTick, long storedTick) {
        return coverage.supersedes(stored == null ? null : stored.coverage, newTick, storedTick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("loadedColumns", coverage.loadedColumns());
        tag.putInt("totalColumns", coverage.totalColumns());
        tag.putInt("admitted", admitted);
        return tag;
    }

    /**
     * Pre-R4 rows without column counts load as optimistic full coverage; {@code withheld} is never
     * reinterpreted (V1-R4 migration rule).
     */
    public static ObservationQuality load(CompoundTag tag) {
        if (tag == null) {
            return fullCoverage(0);
        }
        int admitted = tag.getInt("admitted");
        if (tag.contains("loadedColumns") && tag.contains("totalColumns")) {
            return new ObservationQuality(
                    new PerceptionCoverage(tag.getInt("loadedColumns"), tag.getInt("totalColumns")),
                    admitted);
        }
        return new ObservationQuality(PerceptionCoverage.optimisticFull(), admitted);
    }
}
