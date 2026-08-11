package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentOpinionRouteRankerTest {

    @AfterEach
    void clearGate() {
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void meanAggregationPreventsExtraLabelsFromMultiplyingRoutePower() {
        OpinionFeatureGate.setTestOverride(true);
        EnvironmentOpinionMemory memory = new EnvironmentOpinionMemory();
        memory.recordOutcome(EnvironmentProfile.of(EnvironmentKind.FOREST), 100f);
        memory.recordOutcome(EnvironmentProfile.of(EnvironmentKind.SNOWY), 100f);

        int forest = EnvironmentOpinionRouteRanker.routeBias(
                memory, EnvironmentProfile.of(EnvironmentKind.FOREST));
        int both = EnvironmentOpinionRouteRanker.routeBias(
                memory, EnvironmentProfile.of(EnvironmentKind.FOREST, EnvironmentKind.SNOWY));

        assertEquals(forest, both);
        assertEquals(EnvironmentOpinionRouteRanker.MAX_ROUTE_BIAS, both);
    }

    @Test
    void environmentCannotEraseVisitedOrAntiFixationPenalties() {
        assertTrue(20 > EnvironmentOpinionRouteRanker.MAX_ROUTE_BIAS);
        assertTrue(100 > EnvironmentOpinionRouteRanker.MAX_ROUTE_BIAS);
    }

    @Test
    void disabledOrNeutralProfileHasNoRouteEffect() {
        EnvironmentOpinionMemory memory = new EnvironmentOpinionMemory();
        memory.recordOutcome(EnvironmentProfile.of(EnvironmentKind.FOREST), 100f);

        OpinionFeatureGate.setTestOverride(false);
        assertEquals(0, EnvironmentOpinionRouteRanker.routeBias(
                memory, EnvironmentProfile.of(EnvironmentKind.FOREST)));

        OpinionFeatureGate.setTestOverride(true);
        assertEquals(0, EnvironmentOpinionRouteRanker.routeBias(memory, EnvironmentProfile.empty()));
    }
}
