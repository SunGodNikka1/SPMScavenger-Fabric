package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class V3RowPreconditionTest {

    @Test
    void noShelterHoldIsReadyRegardlessOfClockPhase() {
        assertEquals(V3RowPrecondition.Verdict.READY,
                V3RowPrecondition.evaluate(false, false).verdict());
        assertEquals(V3RowPrecondition.Verdict.READY,
                V3RowPrecondition.evaluate(true, false).verdict());
    }

    @Test
    void shelterHoldBeforeDaytimeWaitsWithoutStartingRow() {
        assertEquals(V3RowPrecondition.Verdict.WAITING_DAYTIME,
                V3RowPrecondition.evaluate(false, true).verdict());
    }

    @Test
    void shelterHoldDuringDaytimeIsFixtureIncomplete() {
        assertEquals(V3RowPrecondition.Verdict.FIXTURE_INCOMPLETE,
                V3RowPrecondition.evaluate(true, true).verdict());
    }
}
