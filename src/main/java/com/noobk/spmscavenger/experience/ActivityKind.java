package com.noobk.spmscavenger.experience;

/**
 * GAO-0b — subjective activity identity for opinion learning (D-GAO-027).
 *
 * <p>Deliberately distinct from scheduler-facing {@link com.noobk.spmscavenger.activity.ActivityClass}.
 * Campfire is a REST anchor, not its own kind.
 */
public enum ActivityKind {
    OVERLAND_EXPLORATION,
    CAVE_EXPLORATION,
    CONTROLLED_DESCENT,
    TUNNEL_SEARCH,
    RESOURCE_GATHERING,
    REST,
    SOCIALIZING,
    MIMICRY
}
