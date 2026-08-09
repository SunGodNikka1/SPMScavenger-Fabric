package com.noobk.spmscavenger.mining;

/**
 * Bounded mining session intent (D-MIW-001). Policy state only — not a registered Goal.
 *
 * <p>Gen-1 code activates {@link #CONTROLLED_DESCENT} with MI-7E; other modes are catalogued for
 * MI-14 director work.
 */
public enum MiningProjectMode {
    CAVE_EXPLORATION,
    SURFACE_EXPOSED,
    CONTROLLED_DESCENT,
    TUNNEL_SEARCH,
    VEIN_EXTRACTION,
    TARGETED_RETURN,
    EMERGENCY_EXIT
}
