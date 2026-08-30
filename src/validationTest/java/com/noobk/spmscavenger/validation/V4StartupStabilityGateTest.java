package com.noobk.spmscavenger.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V4StartupStabilityGateTest {

    @Test
    void attachmentInCreationTickCannotOpenBootstrap() {
        assertEquals(V4StartupStabilityGate.Verdict.WAITING,
                evaluate(100L, 100L, 0, 0, true, true, true).verdict());
    }

    @Test
    void firstLaterServerTickMustAlsoBeAnActualSubjectTick() {
        assertEquals(V4StartupStabilityGate.Verdict.WAITING,
                evaluate(100L, 101L, 0, 0, true, true, true).verdict());
        assertEquals(V4StartupStabilityGate.Verdict.PASS,
                evaluate(100L, 101L, 0, 1, true, true, true).verdict());
    }

    @Test
    void anyRequiredEntityUnavailableBeforeStabilityIsFixtureFailure() {
        assertEquals(V4StartupStabilityGate.Verdict.FIXTURE_FAILURE,
                evaluate(100L, 101L, 0, 1, false, true, true).verdict());
        assertEquals(V4StartupStabilityGate.Verdict.FIXTURE_FAILURE,
                evaluate(100L, 101L, 0, 1, true, false, true).verdict());
        assertEquals(V4StartupStabilityGate.Verdict.FIXTURE_FAILURE,
                evaluate(100L, 101L, 0, 1, true, true, false).verdict());
    }

    @Test
    void noTickProgressWithinBoundFailsFixtureRatherThanOpeningBootstrap() {
        assertEquals(V4StartupStabilityGate.Verdict.FIXTURE_FAILURE,
                evaluate(100L, 120L, 0, 0, true, true, true).verdict());
    }

    private static V4StartupStabilityGate.Assessment evaluate(
            long created, long now, int initialTickCount, int currentTickCount,
            boolean subject, boolean trader, boolean helper) {
        return V4StartupStabilityGate.evaluate(
                created, now, created + 20L, initialTickCount, currentTickCount,
                subject, trader, helper);
    }
}
