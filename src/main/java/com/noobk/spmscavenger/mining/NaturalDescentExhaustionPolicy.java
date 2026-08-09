package com.noobk.spmscavenger.mining;

/**
 * MI-7C — evidence-based natural descent exhaustion (D-MIW-034).
 *
 * <p>Pure policy: callers supply perception and budget evidence; no world access here.
 */
public final class NaturalDescentExhaustionPolicy {

    private NaturalDescentExhaustionPolicy() {
    }

    /**
     * @param activeCaveOpportunity MI-6F commitment still held and valid
     * @param reachableNaturalDescent a legitimate natural descent landing is pathable now
     * @param blockedOpportunity a descent candidate exists but path/combat/hazard blocks it
     * @param descentSearchActive mob is on a DESCENT expedition or equivalent search
     */
    public static NaturalDescentStatus evaluate(
            MiningBudget budget,
            MiningBudgetUsage usage,
            boolean activeCaveOpportunity,
            boolean reachableNaturalDescent,
            boolean blockedOpportunity,
            boolean descentSearchActive) {
        if (activeCaveOpportunity || reachableNaturalDescent) {
            if (blockedOpportunity && !reachableNaturalDescent) {
                return NaturalDescentStatus.TEMPORARILY_BLOCKED;
            }
            return NaturalDescentStatus.AVAILABLE;
        }
        if (blockedOpportunity) {
            return NaturalDescentStatus.TEMPORARILY_BLOCKED;
        }
        boolean budgetConsumed = budget.isSearchBudgetConsumed(usage);
        boolean spatialCoverage = budget.hasSpatialCoverage(usage);
        if (budgetConsumed
                && !activeCaveOpportunity
                && !reachableNaturalDescent
                && spatialCoverage) {
            return NaturalDescentStatus.EXHAUSTED;
        }
        if (descentSearchActive || !budgetConsumed) {
            return NaturalDescentStatus.SEARCHING;
        }
        // Budget consumed without spatial coverage — keep searching; standing still must not exhaust.
        return NaturalDescentStatus.SEARCHING;
    }

    public static boolean mayStartControlledDescent(NaturalDescentStatus status) {
        return status == NaturalDescentStatus.EXHAUSTED;
    }
}
