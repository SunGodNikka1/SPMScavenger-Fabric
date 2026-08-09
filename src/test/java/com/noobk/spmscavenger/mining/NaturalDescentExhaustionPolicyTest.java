package com.noobk.spmscavenger.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalDescentExhaustionPolicyTest {

    private static final MiningBudget SEARCH = MiningBudget.naturalDescentSearchDefaults();

    @Test
    void activeCaveOpportunityYieldsAvailable() {
        assertEquals(
                NaturalDescentStatus.AVAILABLE,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, MiningBudgetUsage.EMPTY, true, false, false, true));
    }

    @Test
    void reachableLandingYieldsAvailable() {
        assertEquals(
                NaturalDescentStatus.AVAILABLE,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, MiningBudgetUsage.EMPTY, false, true, false, true));
    }

    @Test
    void blockedWithoutReachableYieldsTemporarilyBlocked() {
        assertEquals(
                NaturalDescentStatus.TEMPORARILY_BLOCKED,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, MiningBudgetUsage.EMPTY, false, false, true, true));
    }

    @Test
    void mustNotHappen_failuresAloneWithoutCoverageNeverExhaust() {
        MiningBudgetUsage failuresOnly = MiningBudgetUsage.EMPTY.withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep();
        assertEquals(
                NaturalDescentStatus.SEARCHING,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, failuresOnly, false, false, false, true));
    }

    @Test
    void mustHappen_exhaustedRequiresBudgetCoverageAndNoOpportunity() {
        MiningBudgetUsage covered = MiningBudgetUsage.EMPTY
                .withProgress(MiningBudget.NATURAL_SEARCH_MIN_HORIZONTAL, 0)
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep();
        assertTrue(SEARCH.isSearchBudgetConsumed(covered));
        assertTrue(SEARCH.hasSpatialCoverage(covered));
        assertEquals(
                NaturalDescentStatus.EXHAUSTED,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, covered, false, false, false, true));
    }

    @Test
    void activeCaveCommitmentBlocksExhausted() {
        MiningBudgetUsage covered = MiningBudgetUsage.EMPTY
                .withProgress(MiningBudget.NATURAL_SEARCH_MIN_HORIZONTAL, 0)
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep()
                .withFailedStep();
        assertEquals(
                NaturalDescentStatus.AVAILABLE,
                NaturalDescentExhaustionPolicy.evaluate(
                        SEARCH, covered, true, false, false, true));
    }

    @Test
    void mayStartControlledDescentOnlyWhenExhausted() {
        assertFalse(NaturalDescentExhaustionPolicy.mayStartControlledDescent(
                NaturalDescentStatus.SEARCHING));
        assertTrue(NaturalDescentExhaustionPolicy.mayStartControlledDescent(
                NaturalDescentStatus.EXHAUSTED));
    }
}
