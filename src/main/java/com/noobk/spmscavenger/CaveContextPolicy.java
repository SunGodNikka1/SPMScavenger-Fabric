package com.noobk.spmscavenger;

import java.util.Arrays;

/**
 * MI-6 — subterranean / ravine context and explore landing ranking without MiningMemory.
 *
 * <p>MI-6B adds local-rim ravine detection. MI-6D combines descent + cave continuation. MI-6C scores
 * gather opportunities by candidate context. MI-6A supplies non-heightmap floors via
 * {@link CaveLandingResolver}.
 */
public final class CaveContextPolicy {

    /** Feet this far below column surface or local rim count as cave/ravine-like. */
    public static final int MIN_DEPTH_BELOW_SURFACE = 8;

    /** Horizontal radius for rim sampling (blocks). */
    public static final int RIM_SAMPLE_RADIUS = 8;

    /** Added to gather priority tiers when cave opportunity applies to ore. */
    public static final int CAVE_ORE_PRIORITY_BONUS = 15;

    public enum LandingMode {
        NORMAL,
        DESCENT,
        CAVE_CONTINUATION,
        DESCENT_IN_CAVE
    }

    private CaveContextPolicy() {
    }

    public static boolean isCaveLike(int feetY, int surfaceY) {
        return surfaceY - feetY >= MIN_DEPTH_BELOW_SURFACE;
    }

    /**
     * Open ravines have column surface ≈ feet Y; surrounding rim stays high. Enclosed caves have
     * column surface high above feet.
     */
    public static boolean isCaveOrRavineLike(int feetY, int columnSurfaceY, int localRimY) {
        return isCaveLike(feetY, columnSurfaceY) || isCaveLike(feetY, localRimY);
    }

    /**
     * Upper-median of surrounding surface samples (robust to one outlier spike).
     * Empty → {@link Integer#MIN_VALUE}.
     */
    public static int localRimHeight(int... surroundingSurfaces) {
        if (surroundingSurfaces == null || surroundingSurfaces.length == 0) {
            return Integer.MIN_VALUE;
        }
        int[] copy = Arrays.copyOf(surroundingSurfaces, surroundingSurfaces.length);
        Arrays.sort(copy);
        // Upper median: element at ceil((n-1)*0.75) among sorted ascending.
        int index = (copy.length * 3) / 4;
        if (index >= copy.length) {
            index = copy.length - 1;
        }
        return copy[index];
    }

    /** Eight compass offsets at {@link #RIM_SAMPLE_RADIUS}. */
    public static int[] rimSampleOffsetsX() {
        int r = RIM_SAMPLE_RADIUS;
        return new int[] {0, r, r, r, 0, -r, -r, -r};
    }

    public static int[] rimSampleOffsetsZ() {
        int r = RIM_SAMPLE_RADIUS;
        return new int[] {-r, -r, 0, r, r, r, 0, -r};
    }

    public static LandingMode resolveLandingMode(boolean descending, boolean caveOrRavine) {
        if (descending && caveOrRavine) {
            return LandingMode.DESCENT_IN_CAVE;
        }
        if (descending) {
            return LandingMode.DESCENT;
        }
        if (caveOrRavine) {
            return LandingMode.CAVE_CONTINUATION;
        }
        return LandingMode.NORMAL;
    }

    public static boolean isOreResource(GatherIntentPolicy.Resource resource) {
        return resource == GatherIntentPolicy.Resource.COAL
                || resource == GatherIntentPolicy.Resource.RAW_IRON
                || resource == GatherIntentPolicy.Resource.DIAMOND;
    }

    /**
     * MI-6C — bonus when the <em>opportunity</em> is underground/ravine-like, not only the mob.
     *
     * @param candidateColumnCaveLike ore column surface − oreY ≥ 8
     * @param candidateUnderMobRim ore still below the mob's local rim (ravine continuation)
     */
    public static boolean caveOpportunity(
            boolean mobCaveOrRavine,
            boolean candidateColumnCaveLike,
            boolean candidateUnderMobRim) {
        return candidateColumnCaveLike || (mobCaveOrRavine && candidateUnderMobRim);
    }

    public static int orePriorityBonus(boolean caveOpportunity, GatherIntentPolicy.Resource resource) {
        if (!caveOpportunity || resource == null || !isOreResource(resource)) {
            return 0;
        }
        return CAVE_ORE_PRIORITY_BONUS;
    }

    /**
     * Landing sort key. Lower wins.
     *
     * @param terrainRef column surface or local rim used to judge "still underground"
     */
    public static int landingPreferenceKey(
            LandingMode mode, int landingY, int mobY, int terrainRef) {
        return switch (mode) {
            case NORMAL -> Math.abs(landingY - mobY);
            case DESCENT -> DescentPressurePolicy.landingPreferenceKey(landingY, mobY);
            case CAVE_CONTINUATION -> {
                int underFirst = isCaveLike(landingY, terrainRef) ? 0 : 1;
                yield underFirst * 1_000 + Math.abs(landingY - mobY);
            }
            case DESCENT_IN_CAVE -> {
                // Remain underground/ravine-like first, then prefer lower absolute Y, then nearer.
                int underFirst = isCaveLike(landingY, terrainRef) ? 0 : 1;
                yield underFirst * 1_000_000 + landingY * 1_000 + Math.abs(landingY - mobY);
            }
        };
    }

    /** @deprecated prefer {@link #landingPreferenceKey(LandingMode, int, int, int)} */
    @Deprecated
    public static int landingPreferenceKey(int landingY, int mobY, int surfaceY) {
        return landingPreferenceKey(LandingMode.CAVE_CONTINUATION, landingY, mobY, surfaceY);
    }
}
