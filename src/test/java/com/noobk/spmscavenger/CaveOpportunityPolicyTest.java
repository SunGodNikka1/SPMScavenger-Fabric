package com.noobk.spmscavenger;

import com.noobk.spmscavenger.CaveOpportunityPolicy.CaveOpportunity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-6F — the arbitration that stops branch thrash. Pure, so the failure it prevents is testable
 * without a level: the defect was never one bad decision, it was a sequence of individually
 * defensible ones.
 */
class CaveOpportunityPolicyTest {

    private static final long LEFT = 1L;
    private static final long RIGHT = 2L;

    @Test
    void mustHappen_uncommittedTakesTheFirstCandidate() {
        CaveOpportunity chosen = CaveOpportunityPolicy.arbitrate(null, false, LEFT, 40, 0L);
        assertEquals(LEFT, chosen.id());
        assertEquals(40, chosen.score());
    }

    @Test
    void mustNotHappen_aMarginallyBetterBranchStealsTheCommitment() {
        CaveOpportunity held = new CaveOpportunity(LEFT, 40, 0L);
        // Exactly at the margin is not "more than" the margin.
        assertFalse(CaveOpportunityPolicy.shouldSwitch(
                held, RIGHT, 40 + CaveOpportunityPolicy.SWITCH_MARGIN,
                CaveOpportunityPolicy.SWITCH_MARGIN));
        assertSame(held, CaveOpportunityPolicy.arbitrate(held, true, RIGHT, 47, 10L));
    }

    @Test
    void mustHappen_aDecisivelyBetterBranchWins() {
        CaveOpportunity held = new CaveOpportunity(LEFT, 40, 0L);
        CaveOpportunity chosen = CaveOpportunityPolicy.arbitrate(held, true, RIGHT, 60, 10L);
        assertEquals(RIGHT, chosen.id());
    }

    @Test
    void mustNotHappen_theMobFlickersBetweenTwoComparableBranches() {
        // The exact defect: alternating evaluations, each marginally favouring the other side.
        CaveOpportunity held = CaveOpportunityPolicy.arbitrate(null, false, LEFT, 40, 0L);
        long now = 0L;
        for (int i = 0; i < 20; i++) {
            now += 5L;
            int challengerScore = 40 + (i % 2 == 0 ? 3 : -3);
            long challenger = (i % 2 == 0) ? RIGHT : LEFT;
            held = CaveOpportunityPolicy.arbitrate(held, true, challenger, challengerScore, now);
        }
        assertEquals(LEFT, held.id(), "20 marginal re-rankings must not move the commitment");
        assertEquals(0L, held.committedTick(), "and must not refresh it either");
    }

    @Test
    void mustHappen_expiryReleasesTheCommitment() {
        CaveOpportunity held = new CaveOpportunity(LEFT, 40, 0L);
        long past = CaveOpportunityPolicy.COMMIT_TICKS;
        assertFalse(CaveOpportunityPolicy.holds(
                held, true, past, CaveOpportunityPolicy.COMMIT_TICKS));
        assertEquals(RIGHT,
                CaveOpportunityPolicy.arbitrate(held, true, RIGHT, 41, past).id(),
                "after expiry even a marginal challenger may win");
    }

    @Test
    void mustHappen_anInvalidatedOpportunityIsAbandonedImmediately() {
        CaveOpportunity held = new CaveOpportunity(LEFT, 40, 0L);
        assertFalse(CaveOpportunityPolicy.holds(held, false, 1L, CaveOpportunityPolicy.COMMIT_TICKS));
        assertEquals(RIGHT, CaveOpportunityPolicy.arbitrate(held, false, RIGHT, 1, 1L).id(),
                "a collapsed or unreachable opportunity must not be defended by hysteresis");
    }

    @Test
    void mustNotHappen_reofferingTheSameOpportunityCountsAsASwitch() {
        CaveOpportunity held = new CaveOpportunity(LEFT, 40, 0L);
        assertFalse(CaveOpportunityPolicy.shouldSwitch(
                held, LEFT, 999, CaveOpportunityPolicy.SWITCH_MARGIN));
        assertSame(held, CaveOpportunityPolicy.arbitrate(held, true, LEFT, 999, 10L),
                "a rising score on the current choice changes nothing");
    }

    @Test
    void mustHappen_commitmentTimestampSurvivesSoExpiryIsMeasuredFromTheDecision() {
        CaveOpportunity held = CaveOpportunityPolicy.arbitrate(null, false, LEFT, 40, 100L);
        held = CaveOpportunityPolicy.arbitrate(held, true, RIGHT, 41, 150L);
        assertEquals(100L, held.committedTick(),
                "a refused challenger must not extend the commitment window");
        assertNotEquals(150L, held.committedTick());
    }
}
