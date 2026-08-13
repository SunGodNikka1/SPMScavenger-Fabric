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
    /** GAO-5B — route bias magnitude; see {@link PlaceOpinionRouteRanker}. */
    public static final int PLACE_ROUTE_BIAS = PlaceOpinionRouteRanker.MAX_ROUTE_BIAS;

    /** @deprecated GAO-5B removed current-position place utility; use {@link PlaceOpinionRouteRanker}. */
    @Deprecated
    public static final float PLACE_PREFERENCE = 22f;

    /**
     * SOCIAL sits between EXPLORE and REST at rest: it is worth doing, but never so intrinsically
     * valuable that a mob with no one nearby should feel deprived. Opportunity, not appetite, is
     * what makes it competitive — and opportunity is gated outside scoring.
     */
    public static final float BASE_USEFULNESS_SOCIAL = 9f;

    /** Personality is the strongest single term: a sociable mob genuinely prefers company. */
    public static final float SOCIAL_SOCIABILITY_FIT = 32f;

    /** How this mob feels about <em>this</em> entity. The reason SOCIAL is subject-specific. */
    public static final float SOCIAL_SUBJECT_PREFERENCE = 26f;

    /** Boredom pushes toward company, as it pushes toward exploring. */
    public static final float SOCIAL_BOREDOM_FIT = 22f;

    /** Stress pushes away from it — a frightened mob withdraws rather than socialises. */
    public static final float SOCIAL_STRESS_FIT = 18f;

    /** Cheap: the target is already within the host's own acquisition radius. */
    public static final float SOCIAL_COST = 2f;

    public static final float REST_STRESS_FIT = 30f;
    public static final float REST_BOREDOM_FIT = 20f;
    public static final float REST_COST = 1f;

    private ActivityUtilityWeights() {
    }
}
