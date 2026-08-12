package com.noobk.spmscavenger.opinion;

/**
 * GAO-4R — why a discretionary executor is not adoptable at decision time.
 *
 * <p>Distinct from {@link DiscretionaryAvailability} (executor installed) and from running
 * incumbent state.
 */
public enum ActivityAdoptionBlocker {
    READY,
    EXECUTOR_DISABLED,
    SHELTER_AUTHORITY,
    COMBAT,
    NO_CAMPFIRE_AVAILABLE,
    NO_CAMPFIRE_ITEM,
    NO_VALID_REST_POSITION,
    SCAN_COOLDOWN,
    MOB_GRIEFING_DISABLED,
    /** Explore-specific gates; see {@link ActivityAdmission#detail()}. */
    EXPLORE_NOT_READY
}
