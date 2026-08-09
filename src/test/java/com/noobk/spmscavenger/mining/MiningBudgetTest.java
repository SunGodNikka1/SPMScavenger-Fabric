package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningBudgetTest {

    @Test
    void naturalSearchDefaultsCapFailuresNotBlocks() {
        MiningBudget budget = MiningBudget.naturalDescentSearchDefaults();
        assertEquals(0, budget.maxBlocksMined());
        assertEquals(6, budget.maxFailedSteps());
    }

    @Test
    void searchBudgetConsumedOnFailuresTicksOrDistance() {
        MiningBudget budget = MiningBudget.naturalDescentSearchDefaults();
        MiningBudgetUsage byFailures = MiningBudgetUsage.EMPTY;
        for (int i = 0; i < budget.maxFailedSteps(); i++) {
            byFailures = byFailures.withFailedStep();
        }
        assertTrue(budget.isSearchBudgetConsumed(byFailures));

        MiningBudgetUsage byTicks = MiningBudgetUsage.EMPTY;
        for (int i = 0; i < budget.maxTicks(); i++) {
            byTicks = byTicks.withTick();
        }
        assertTrue(budget.isSearchBudgetConsumed(byTicks));
    }

    @Test
    void spatialCoverageRequiresMovement() {
        MiningBudget budget = MiningBudget.naturalDescentSearchDefaults();
        assertFalse(budget.hasSpatialCoverage(MiningBudgetUsage.EMPTY));

        NaturalDescentSearchState state = new NaturalDescentSearchState();
        state.beginSearch(new BlockPos(0, 80, 0));
        state.recordPosition(new BlockPos(MiningBudget.NATURAL_SEARCH_MIN_HORIZONTAL, 80, 0));
        assertTrue(budget.hasSpatialCoverage(state.usage()));
    }
}
