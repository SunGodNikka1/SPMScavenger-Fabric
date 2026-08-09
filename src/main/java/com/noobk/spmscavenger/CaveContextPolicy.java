package com.noobk.spmscavenger;

/**
 * MI-6 — cheap subterranean / ravine heuristic so exposed ore can be preferred while the mob is
 * already under the open-sky surface, without MiningMemory or clairvoyant scans.
 */
public final class CaveContextPolicy {

    /** Feet this far below the motion-blocking surface count as cave/ravine-like. */
    public static final int MIN_DEPTH_BELOW_SURFACE = 8;

    /** Added to gather priority tiers when cave-like and the candidate is ore. */
    public static final int CAVE_ORE_PRIORITY_BONUS = 15;

    private CaveContextPolicy() {
    }

    public static boolean isCaveLike(int mobY, int surfaceY) {
        return surfaceY - mobY >= MIN_DEPTH_BELOW_SURFACE;
    }

    public static boolean isOreResource(GatherIntentPolicy.Resource resource) {
        return resource == GatherIntentPolicy.Resource.COAL
                || resource == GatherIntentPolicy.Resource.RAW_IRON
                || resource == GatherIntentPolicy.Resource.DIAMOND;
    }

    /** Bonus applied inside {@link GatherTargetPolicy} priority tiers (before ×256 scaling). */
    public static int orePriorityBonus(boolean caveLike, GatherIntentPolicy.Resource resource) {
        if (!caveLike || resource == null || !isOreResource(resource)) {
            return 0;
        }
        return CAVE_ORE_PRIORITY_BONUS;
    }

    /**
     * Landing sort key when continuing a subterranean walk: prefer standables still under the
     * local surface, then nearer elevation. Lower keys win.
     */
    public static int landingPreferenceKey(int landingY, int mobY, int surfaceY) {
        int underFirst = isCaveLike(landingY, surfaceY) ? 0 : 1;
        return underFirst * 1_000 + Math.abs(landingY - mobY);
    }
}
