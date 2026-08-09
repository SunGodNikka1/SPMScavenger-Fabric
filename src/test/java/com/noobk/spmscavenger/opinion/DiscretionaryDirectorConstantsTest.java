package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscretionaryDirectorConstantsTest {

    @Test
    void pendingTtlMatchesObservationCadence() {
        assertEquals(200, DiscretionaryDirectorConstants.PENDING_INTENT_TTL_TICKS);
        assertTrue(DiscretionaryDirectorConstants.PENDING_INTENT_TTL_TICKS % 10 == 0);
    }

    @Test
    void minCommitmentMatchesExploringGoalCooldownHorizon() {
        assertEquals(600, DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS);
    }
}
