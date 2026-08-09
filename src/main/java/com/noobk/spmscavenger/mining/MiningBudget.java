package com.noobk.spmscavenger.mining;

/**
 * Per-trip excavation caps (D-MIW-010). Usage counters and exhaustion ship in MI-7B.
 */
public record MiningBudget(
        int maxBlocksMined,
        int maxDistanceFromAnchor,
        int maxTicks,
        int maxFailedSteps,
        int maxVerticalProgress) {

    /** Minimum horizontal travel from search anchor before exhaustion may declare. */
    public static final int NATURAL_SEARCH_MIN_HORIZONTAL = 24;

    /** Minimum vertical descent from search start before exhaustion may declare. */
    public static final int NATURAL_SEARCH_MIN_VERTICAL = 8;

    /** Gen-1 controlled-descent defaults from RFC MI-7 topic. */
    public static MiningBudget controlledDescentDefaults() {
        return new MiningBudget(64, 48, 2400, 3, 32);
    }

    /**
     * Natural-descent search budget — no deliberate digging; caps exploration effort before MI-7E.
     */
    public static MiningBudget naturalDescentSearchDefaults() {
        return new MiningBudget(0, 64, 2400, 6, 32);
    }

    public boolean isBlocksExhausted(MiningBudgetUsage usage) {
        return maxBlocksMined > 0 && usage.blocksMined() >= maxBlocksMined;
    }

    public boolean isDistanceExhausted(MiningBudgetUsage usage) {
        return maxDistanceFromAnchor > 0
                && usage.maxHorizontalDistance() >= maxDistanceFromAnchor;
    }

    public boolean isTicksExhausted(MiningBudgetUsage usage) {
        return maxTicks > 0 && usage.ticksElapsed() >= maxTicks;
    }

    public boolean isFailuresExhausted(MiningBudgetUsage usage) {
        return maxFailedSteps > 0 && usage.failedSteps() >= maxFailedSteps;
    }

    /**
     * True when any search axis has hit its cap. Natural search uses ticks, distance, and failures;
     * blocks are zero for natural descent.
     */
    public boolean isSearchBudgetConsumed(MiningBudgetUsage usage) {
        return isTicksExhausted(usage) || isDistanceExhausted(usage) || isFailuresExhausted(usage);
    }

    /**
     * Spatial coverage — mob actually moved while searching, not merely failed path creation in place.
     */
    public boolean hasSpatialCoverage(MiningBudgetUsage usage) {
        return usage.maxHorizontalDistance() >= NATURAL_SEARCH_MIN_HORIZONTAL
                || usage.verticalDescent() >= NATURAL_SEARCH_MIN_VERTICAL;
    }
}
