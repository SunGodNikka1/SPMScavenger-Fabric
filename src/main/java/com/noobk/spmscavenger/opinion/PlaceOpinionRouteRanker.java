package com.noobk.spmscavenger.opinion;

import net.minecraft.world.level.ChunkPos;

/**
 * GAO-5B — soft place-opinion bias for expedition route ranking.
 *
 * <p>Place memory ranks among already-valid candidate destinations. It must never veto mandatory
 * mining, descent admission, or exploration outright.
 *
 * <p>Future: when {@link com.noobk.spmscavenger.DescentHeadingPolicy} returns multiple equally-valid
 * descent headings, apply {@link #destinationBias} as the same soft tie-breaker — not as a gate on
 * required descent.
 */
public final class PlaceOpinionRouteRanker {

    /**
     * Max additive route score from place memory. Sized below anti-fixation penalties (e.g.
     * {@code -100} for a recently visited expedition region in {@code ExploringGoal}) so history
     * dominates fixation, and place remains a tie-breaker among comparable routes.
     */
    public static final int MAX_ROUTE_BIAS = 15;

    private PlaceOpinionRouteRanker() {
    }

    /** Soft bias for a candidate destination block coordinate. Returns {@code 0} when opinion off. */
    public static int destinationBias(PlaceOpinionMemory places, int blockX, int blockZ) {
        if (!OpinionFeatureGate.isEnabled()) {
            return 0;
        }
        return scaledBias(places.preference(new ChunkPos(blockX, blockZ)));
    }

    /** Bias from the final waypoint of a candidate route (where the expedition is headed). */
    public static int routeBias(PlaceOpinionMemory places, int finalDestX, int finalDestZ) {
        return destinationBias(places, finalDestX, finalDestZ);
    }

    static int scaledBias(float preference) {
        return Math.round(UtilityNormalizer.channel(preference) * MAX_ROUTE_BIAS);
    }
}
