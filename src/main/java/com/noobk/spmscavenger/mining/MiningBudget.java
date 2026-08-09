package com.noobk.spmscavenger.mining;

/**
 * Per-trip excavation caps (D-MIW-010). Exhaustion checks and usage counters ship in MI-7B.
 */
public record MiningBudget(
        int maxBlocksMined,
        int maxDistanceFromAnchor,
        int maxTicks,
        int maxFailedSteps,
        int maxVerticalProgress) {

    /** Gen-1 controlled-descent defaults from RFC MI-7 topic. */
    public static MiningBudget controlledDescentDefaults() {
        return new MiningBudget(64, 48, 2400, 3, 32);
    }
}
