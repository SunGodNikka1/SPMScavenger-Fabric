package com.noobk.spmscavenger.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

/**
 * V1-R4 — how much of the observation footprint the mob could legitimately perceive.
 *
 * <p>Computed from <b>loaded chunk columns</b> intersecting the horizontal {@link
 * VillagePerception#VILLAGE_QUERY_RADIUS} cylinder around the origin. This is independent of
 * {@code PoiManager} results: no property of an unloaded {@code PoiRecord} may influence coverage.
 */
public record PerceptionCoverage(int loadedColumns, int totalColumns) {

    /**
     * Optimistic full coverage for pre-R4 NBT rows. Deliberately not {@code (0, 0)} so
     * {@link #isFull()} holds and a partial post-upgrade glance cannot degrade a stored anchor.
     */
    public static final PerceptionCoverage OPTIMISTIC_FULL = new PerceptionCoverage(1, 1);

    public PerceptionCoverage {
        if (loadedColumns < 0 || totalColumns < 0) {
            throw new IllegalArgumentException(
                    "column counts must be non-negative: " + loadedColumns + "/" + totalColumns);
        }
        if (loadedColumns > totalColumns) {
            throw new IllegalArgumentException(
                    "loaded columns cannot exceed total: " + loadedColumns + "/" + totalColumns);
        }
    }

    public static PerceptionCoverage optimisticFull() {
        return OPTIMISTIC_FULL;
    }

    /**
     * Pipeline A — coverage only; must run before any {@code PoiManager} query.
     */
    public static PerceptionCoverage compute(ServerLevel level, BlockPos origin, int radius) {
        int originX = origin.getX();
        int originZ = origin.getZ();
        int minChunkX = SectionPos.blockToSectionCoord(originX - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(originX + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(originZ - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(originZ + radius);
        long radiusSq = (long) radius * radius;

        int total = 0;
        int loaded = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!intersectsHorizontalCircle(chunkX, chunkZ, originX, originZ, radiusSq)) {
                    continue;
                }
                total++;
                if (level.hasChunk(chunkX, chunkZ)) {
                    loaded++;
                }
            }
        }
        return new PerceptionCoverage(loaded, total);
    }

    static boolean intersectsHorizontalCircle(
            int chunkX, int chunkZ, int originX, int originZ, long radiusSq) {
        int minBlockX = chunkX << 4;
        int maxBlockX = minBlockX + 15;
        int minBlockZ = chunkZ << 4;
        int maxBlockZ = minBlockZ + 15;
        int closestX = Math.max(minBlockX, Math.min(originX, maxBlockX));
        int closestZ = Math.max(minBlockZ, Math.min(originZ, maxBlockZ));
        long dx = (long) closestX - originX;
        long dz = (long) closestZ - originZ;
        return dx * dx + dz * dz <= radiusSq;
    }

    public boolean isFull() {
        return totalColumns > 0 && loadedColumns == totalColumns;
    }

    /** Derived view for logging only — not serialized. */
    public float ratio() {
        return totalColumns == 0 ? 0f : (float) loadedColumns / totalColumns;
    }

    /**
     * Positive when {@code this} represents strictly more perceivable footprint than {@code other}.
     */
    public int compareTo(PerceptionCoverage other) {
        if (other == null) {
            return 1;
        }
        long left = (long) loadedColumns * other.totalColumns;
        long right = (long) other.loadedColumns * totalColumns;
        return Long.compare(left, right);
    }

    public boolean supersedes(PerceptionCoverage stored, long newTick, long storedTick) {
        if (stored == null) {
            return true;
        }
        int cmp = compareTo(stored);
        if (cmp > 0) {
            return true;
        }
        if (cmp < 0) {
            return false;
        }
        return newTick > storedTick;
    }
}
