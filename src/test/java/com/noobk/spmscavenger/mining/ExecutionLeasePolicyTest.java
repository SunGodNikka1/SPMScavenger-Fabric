package com.noobk.spmscavenger.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14C1 — Loop A: the zombie assignment.
 *
 * <p>The failure being repaired: {@code canUse} tested config, combat, {@code mobGriefing} and tool
 * capability <em>before</em> looking up the assignment. Any of them failing after assignment made
 * the executor return {@code false} ahead of the lookup, the project stayed {@code RUNNING}, and
 * {@code mayStartControlledDescent} refused every future assignment because one was present. No
 * budget ticked, because budget only ticks during execution — so it could never time out.
 *
 * <p>The contract these tests hold: <b>no {@code RUNNING} project may exist indefinitely without
 * either execution progress or an explicit suspension reason.</b>
 */
class ExecutionLeasePolicyTest {

    private static final long ASSIGNED_AT = 1_000L;

    private static ExecutionLeasePolicy.LeaseOutcome evaluate(
            ExecutionBlocker blocker, boolean everStarted, long heldTicks) {
        return ExecutionLeasePolicy.evaluate(
                blocker, everStarted, ASSIGNED_AT, ASSIGNED_AT + heldTicks);
    }

    @Test
    void mustHappen_anUnblockedAssignmentIsAuthorised() {
        assertTrue(evaluate(ExecutionBlocker.NONE, false, 0).authorized());
        assertTrue(evaluate(ExecutionBlocker.NONE, false, 100_000).authorized(),
                "age alone never blocks an assignment that can actually run");
    }

    // ---- HARD: release immediately ----

    @Test
    void mustHappen_aLostPickaxeReleasesTheAssignmentAtOnce() {
        ExecutionLeasePolicy.LeaseOutcome outcome =
                evaluate(ExecutionBlocker.CAPABILITY_MISSING, false, 0);

        assertTrue(outcome.revoked(),
                "holding a dig assignment while unable to dig blocks the very systems that would "
                        + "restore a pickaxe");
        assertSame(MiningProjectEnd.TOOL_FAILURE, outcome.revokeReason());
    }

    @Test
    void mustHappen_hardWorldBlockersReleaseWithoutWaiting() {
        for (ExecutionBlocker blocker :
                new ExecutionBlocker[] {
                    ExecutionBlocker.FEATURE_DISABLED, ExecutionBlocker.WORLD_RULE_DISABLED
                }) {
            ExecutionLeasePolicy.LeaseOutcome outcome = evaluate(blocker, true, 0);
            assertTrue(outcome.revoked(), blocker + " is not going to resolve itself");
            assertSame(MiningProjectEnd.EXECUTION_UNAVAILABLE, outcome.revokeReason());
        }
    }

    // ---- TEMPORARY: suspend, but bounded ----

    @Test
    void mustHappen_combatSuspendsRatherThanDestroyingTheStaircase() {
        ExecutionLeasePolicy.LeaseOutcome outcome =
                evaluate(ExecutionBlocker.COMBAT_TARGET, true, 200);

        assertEquals(ExecutionLeasePolicy.LeaseDecision.SUSPEND, outcome.decision(),
                "a mob fighting a zombie for ten seconds should keep its dig");
        assertNull(outcome.revokeReason());
    }

    @Test
    void mustNotHappen_aTemporaryBlockerHoldsTheAssignmentForever() {
        long beyondGrace = ExecutionLeasePolicy.TEMPORARY_GRACE_TICKS + 1;
        ExecutionLeasePolicy.LeaseOutcome outcome =
                evaluate(ExecutionBlocker.COMBAT_TARGET, true, beyondGrace);

        assertTrue(outcome.revoked(),
                "an unbounded suspension is the same deadlock wearing a different label");
        assertSame(MiningProjectEnd.COMBAT, outcome.revokeReason());
    }

    // ---- CONTENTION: the original Loop A ----

    @Test
    void mustNotHappen_anAssignmentThatNeverRanIsHeldForever() {
        long beyondStartLease = ExecutionLeasePolicy.START_LEASE_TICKS + 1;

        assertEquals(ExecutionLeasePolicy.LeaseDecision.SUSPEND,
                evaluate(ExecutionBlocker.CONTENTION, false, 10).decision(),
                "brief contention is normal goal churn");
        ExecutionLeasePolicy.LeaseOutcome expired =
                evaluate(ExecutionBlocker.CONTENTION, false, beyondStartLease);
        assertTrue(expired.revoked(),
                "never once admitted within the start lease - release it so something else can be "
                        + "decided");
        assertSame(MiningProjectEnd.LEASE_EXPIRED, expired.revokeReason());
    }

    /**
     * The start lease must not fire on work that <em>is</em> running. Contention against an already
     * started project is MI-14C2's problem, and MI-14C3 adds the progress clock that catches a
     * started-then-starved executor. Revoking here would kill live digs.
     */
    @Test
    void mustNotHappen_aStartedProjectIsRevokedByTheStartLease() {
        ExecutionLeasePolicy.LeaseOutcome outcome = ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.CONTENTION, true, ASSIGNED_AT,
                ASSIGNED_AT + ExecutionLeasePolicy.START_LEASE_TICKS * 10L);

        assertEquals(ExecutionLeasePolicy.LeaseDecision.SUSPEND, outcome.decision(),
                "the start lease measures admission, not progress - MI-14C3 owns the second clock");
    }

    // ---- the invariant itself ----

    @Test
    void mustHappen_everyBlockerEitherAuthorisesSuspendsOrRevokesWithAReason() {
        for (ExecutionBlocker blocker : ExecutionBlocker.values()) {
            for (boolean started : new boolean[] {false, true}) {
                for (long held : new long[] {0, 100, 10_000, 1_000_000}) {
                    ExecutionLeasePolicy.LeaseOutcome outcome = evaluate(blocker, started, held);
                    if (outcome.revoked()) {
                        assertNotNull(outcome.revokeReason(),
                                "a revocation must record why: " + blocker);
                    } else {
                        assertNull(outcome.revokeReason());
                    }
                }
            }
        }
    }

    /**
     * The contract in one assertion: past every bound, no non-executing assignment is still merely
     * suspended. A blocker that could hold one indefinitely is the defect this task exists for.
     */
    @Test
    void mustNotHappen_anyBlockerCanHoldAnUnstartedAssignmentIndefinitely() {
        long past = Math.max(
                ExecutionLeasePolicy.START_LEASE_TICKS,
                ExecutionLeasePolicy.TEMPORARY_GRACE_TICKS) + 1;

        for (ExecutionBlocker blocker : ExecutionBlocker.values()) {
            if (blocker.permitsExecution()) {
                continue;
            }
            assertTrue(evaluate(blocker, false, past).revoked(),
                    blocker + " could strand a RUNNING project with no execution and no bound");
        }
    }

    @Test
    void mustHappen_blockerClassificationMatchesRecoverability() {
        assertSame(ExecutionBlocker.BlockerClass.TEMPORARY,
                ExecutionBlocker.COMBAT_TARGET.blockerClass());
        assertSame(ExecutionBlocker.BlockerClass.HARD,
                ExecutionBlocker.CAPABILITY_MISSING.blockerClass());
        assertSame(ExecutionBlocker.BlockerClass.CONTENTION,
                ExecutionBlocker.CONTENTION.blockerClass());
        assertTrue(ExecutionBlocker.NONE.permitsExecution());
        assertFalse(ExecutionBlocker.CONTENTION.permitsExecution());
    }

    // ---- lease bookkeeping ----

    @Test
    void mustHappen_theLeaseRecordsWhoStartedAndWhen() {
        MiningExecutionLease issued =
                MiningExecutionLease.issued(MiningProjectMode.CONTROLLED_DESCENT, 500L);
        assertFalse(issued.everStarted());
        assertSame(MiningExecutionLease.LeaseState.ASSIGNED, issued.state());

        MiningExecutionLease active = issued.started(700L);
        assertTrue(active.everStarted());
        assertEquals(700L, active.executorStartedAt());
        assertSame(MiningExecutionLease.LeaseState.ACTIVE, active.state());

        assertEquals(700L, active.started(900L).executorStartedAt(),
                "first start wins - the start lease must not be refreshable by re-entry");
        assertSame(MiningExecutionLease.LeaseState.ACTIVE, active.suspended().resumed().state(),
                "resuming a lease that had started returns to ACTIVE, not ASSIGNED");
    }

    @Test
    void mustHappen_leaseSurvivesASaveLoadRound() {
        MiningExecutionLease lease =
                MiningExecutionLease.issued(MiningProjectMode.CONTROLLED_DESCENT, 42L)
                        .started(99L)
                        .suspended();

        assertEquals(lease, MiningExecutionLease.load(lease.save()),
                "an assignment that outlives a restart must keep its clocks, or the lease resets "
                        + "every reload and never expires");
    }
}
