package com.noobk.spmscavenger;

/**
 * How a gather target became legitimately knowable (MI-13 / D-MIW-008).
 *
 * <p>Only modes other than {@link #UNDISCOVERED} may enter the break queue.
 */
public enum DiscoveryMode {
    /** Exposed to air / cave surface at scan time. */
    VISIBLE,
    /** Revealed by the mob's own recent break on an adjacent block. */
    NEWLY_EXPOSED,
    /** Previously sighted ore not yet mined — requires {@code MiningMemory} (deferred). */
    MEMORY,
    /** Short-range continuation of an already-discovered vein (deferred). */
    LOCAL_SEARCH,
    /** Ore or material acquired through SPM loot/container paths — not gather scan. */
    LOOT,
    /** Behind solid stone or otherwise illegitimate for targeting. */
    UNDISCOVERED;

    public boolean isLegitimate() {
        return this != UNDISCOVERED;
    }
}
