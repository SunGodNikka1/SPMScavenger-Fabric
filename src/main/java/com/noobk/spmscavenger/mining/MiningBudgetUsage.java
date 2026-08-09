package com.noobk.spmscavenger.mining;

/**
 * MI-7B — accumulated usage against a {@link MiningBudget} cap set.
 */
public record MiningBudgetUsage(
        int blocksMined,
        int maxHorizontalDistance,
        int ticksElapsed,
        int failedSteps,
        int verticalDescent) {

    public static final MiningBudgetUsage EMPTY = new MiningBudgetUsage(0, 0, 0, 0, 0);

    public MiningBudgetUsage withBlocksMined(int delta) {
        return new MiningBudgetUsage(
                blocksMined + Math.max(0, delta),
                maxHorizontalDistance,
                ticksElapsed,
                failedSteps,
                verticalDescent);
    }

    public MiningBudgetUsage withTick() {
        return new MiningBudgetUsage(
                blocksMined, maxHorizontalDistance, ticksElapsed + 1, failedSteps, verticalDescent);
    }

    public MiningBudgetUsage withFailedStep() {
        return new MiningBudgetUsage(
                blocksMined, maxHorizontalDistance, ticksElapsed, failedSteps + 1, verticalDescent);
    }

    public MiningBudgetUsage withProgress(int horizontalDistance, int verticalDescentFromStart) {
        return new MiningBudgetUsage(
                blocksMined,
                Math.max(maxHorizontalDistance, horizontalDistance),
                ticksElapsed,
                failedSteps,
                Math.max(this.verticalDescent, verticalDescentFromStart));
    }
}
