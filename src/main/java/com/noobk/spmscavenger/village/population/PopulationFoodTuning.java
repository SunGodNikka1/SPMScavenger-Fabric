package com.noobk.spmscavenger.village.population;

/**
 * Provisional tuning for V3-E population food episodes (task-57).
 */
public final class PopulationFoodTuning {

    /** PlayerMob nutrition points that must remain in the edible backpack pool after any debit. */
    public static final int MIN_SURVIVAL_NUTRITION_RESERVE = 12;

    /** Vanilla breeder-local HOME search radius (VillagerMakeLove). */
    public static final int BREEDER_LOCAL_RADIUS = 48;

    /** Post-commit observation window — must exceed toss {@code pickUpDelay}. */
    public static final int ACK_WAIT_TICKS = 40;

    public static final int HANDOFF_PREPARE_TICKS = 2;
    public static final int PATH_TIMEOUT_TICKS = 100;
    public static final double REACH_DISTANCE_SQR = 4.0;

    public static final int EMPTY_SCAN_COOLDOWN = 40;
    public static final int POST_EPISODE_COOLDOWN_TICKS = 200;

    /** Max villager breeding food points per single bounded delivery. */
    public static final int MAX_EPISODE_FOOD_VALUE = 6;

    public static final int TOSS_PICK_UP_DELAY = 10;

    /** Bounded recipient enumeration for path probes. */
    public static final int MAX_RECIPIENT_CANDIDATES = 8;

    private PopulationFoodTuning() {}
}
