package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * MI-6A — bounded local 3D walkable-floor probes for explore landings while already underground.
 * Does not map caves, store memory, or pathfind; callers must validate standability and paths.
 */
public final class CaveLandingResolver {

    public static final int XZ_RADIUS = 4;
    public static final int Y_RADIUS = 6;
    public static final int MAX_CANDIDATES = 16;
    public static final int MAX_PROBES = 180;

    private CaveLandingResolver() {
    }

    /**
     * Collects standable floor positions near {@code (centreX, centreZ)} around {@code mobY}.
     * Probe order prefers the mob's Y band, then downward, expanding horizontally.
     */
    public static List<BlockPos> collectStandable(
            int centreX, int centreZ, int mobY, Predicate<BlockPos> standable) {
        List<BlockPos> accepted = new ArrayList<>(MAX_CANDIDATES);
        int probes = 0;
        int[] dyOrder = dyProbeOrder();
        for (int radius = 0; radius <= XZ_RADIUS && accepted.size() < MAX_CANDIDATES; radius++) {
            for (int dx = -radius; dx <= radius && accepted.size() < MAX_CANDIDATES; dx++) {
                for (int dz = -radius; dz <= radius && accepted.size() < MAX_CANDIDATES; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    for (int dy : dyOrder) {
                        if (accepted.size() >= MAX_CANDIDATES || probes >= MAX_PROBES) {
                            return accepted;
                        }
                        probes++;
                        BlockPos feet = new BlockPos(x, mobY + dy, z);
                        if (standable.test(feet)) {
                            accepted.add(feet.immutable());
                        }
                    }
                }
            }
        }
        return accepted;
    }

    /** 0, −1, +1, −2, +2, … within ±{@link #Y_RADIUS}. */
    static int[] dyProbeOrder() {
        int[] order = new int[Y_RADIUS * 2 + 1];
        order[0] = 0;
        int at = 1;
        for (int step = 1; step <= Y_RADIUS; step++) {
            order[at++] = -step;
            order[at++] = step;
        }
        return order;
    }
}
