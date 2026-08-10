package com.noobk.spmscavenger.opinion;

/**
 * GAO-3 — component weights mapping normalized {@code [-1,+1]} inputs to final utility
 * {@code [-100,+100]} scale.
 */
public final class ActivityUtilityWeights {

    public static final float UTILITY_MIN = -100f;
    public static final float UTILITY_MAX = 100f;

    public static final float BASE_USEFULNESS_EXPLORE = 10f;
    public static final float BASE_USEFULNESS_REST = 8f;

    public static final float PREFERENCE = 40f;
    public static final float REPETITION = 40f;
    public static final float RECENT_REWARD = 12f;
    public static final float FAILURE = 16f;

    public static final float EXPLORE_BOREDOM_FIT = 35f;
    public static final float EXPLORE_STRESS_FIT = 25f;
    public static final float EXPLORE_NOVELTY_FIT = 18f;
    public static final float EXPLORE_COST = 8f;
    public static final float PLACE_PREFERENCE = 22f;

    public static final float REST_STRESS_FIT = 30f;
    public static final float REST_BOREDOM_FIT = 20f;
    public static final float REST_COST = 1f;

    private ActivityUtilityWeights() {
    }
}
