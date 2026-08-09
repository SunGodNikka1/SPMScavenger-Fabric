package com.noobk.spmscavenger;

/**
 * D-MIW-031 — when progression still wants diamond but local gather is ineligible, press explore
 * toward lower ground instead of inventing a surface diamond scan.
 */
public final class DescentPressurePolicy {

    private DescentPressurePolicy() {
    }

    /**
     * @param progressionDemand diamonds still needed for craft (Y-agnostic)
     * @param localGatherEligible feet inside the diamond generation band
     * @param hasLegitimateSighting MEMORY/NEWLY_EXPOSED ore known (gen-1 usually false)
     */
    public static boolean wantsDescentExplore(
            int progressionDemand, boolean localGatherEligible, boolean hasLegitimateSighting) {
        if (progressionDemand <= 0 || hasLegitimateSighting) {
            return false;
        }
        return !localGatherEligible;
    }

    /**
     * Sort key for landing candidates under descent pressure: prefer standable positions below the
     * mob, then nearer elevation change. Lower keys are preferred.
     */
    public static int landingPreferenceKey(int landingY, int mobY) {
        int belowFirst = landingY < mobY ? 0 : 1;
        int elevation = Math.abs(landingY - mobY);
        return belowFirst * 1_000 + elevation;
    }
}
