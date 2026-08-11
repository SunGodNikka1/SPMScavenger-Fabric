package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnticsExpressionPolicyTest {

    @Test
    void legacyGazeRemainsOnlyWhileOpinionIsDisabled() {
        assertTrue(AnticsGoal.mayWriteMimicLook(false));
        assertFalse(AnticsGoal.mayWriteMimicLook(true),
                "flagless Antics must not bypass the scheduler-owned LOOK goal");
    }
}
