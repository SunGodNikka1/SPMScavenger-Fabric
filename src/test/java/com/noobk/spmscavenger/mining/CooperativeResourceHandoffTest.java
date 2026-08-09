package com.noobk.spmscavenger.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cooperative Resource Handoff — productive downstream work must never age the upstream executor's
 * no-progress lease.
 *
 * <h2>The failure this prevents</h2>
 *
 * Tunnel Search exists to expose ore that {@code GatherResourcesGoal} then takes. Granting gather
 * {@code ALLOW} is necessary but not sufficient: the classifier mapped "participating goal, not
 * YIELD" to {@code NOT_MOVE_HOLDER} and therefore {@code ExecutionBlocker.NONE} — for a goal that
 * {@code conflictsWithRequiredFlags} had <em>already proved</em> was holding {@code MOVE}.
 *
 * <p>So while the mob walked to the diamond the tunnel exposed and mined the vein, the lease
 * believed tunnel execution was available and unblocked. Gather can spend 200 ticks approaching plus
 * capped breaks and vein-follow; {@code PROGRESS_LEASE_TICKS} is 400. The tunnel project would be
 * revoked {@code NO_PROGRESS} <b>because the mob was succeeding</b>.
 */
class CooperativeResourceHandoffTest {

    private static final long ASSIGNED_AT = 1_000L;

    @Test
    void mustHappen_cooperativeWorkPausesRatherThanAgesTheLease() {
        assertSame(ExecutionBlocker.BlockerClass.PROTECTED_PAUSE,
                ExecutionBlocker.COOPERATIVE_WORK.blockerClass(),
                "downstream work on the project's behalf is a pause, not contention or failure");
        assertFalse(ExecutionBlocker.COOPERATIVE_WORK.permitsExecution(),
                "the upstream executor genuinely cannot run - something else holds its flags");
    }

    @Test
    void mustNotHappen_cooperativeWorkEverRevokesTheProject() {
        long[] durations = {0, 400, ExecutionLeasePolicy.TEMPORARY_GRACE_TICKS + 1,
                ExecutionLeasePolicy.START_LEASE_TICKS + 1, 100_000};

        for (boolean started : new boolean[] {false, true}) {
            for (long held : durations) {
                ExecutionLeasePolicy.LeaseOutcome outcome = ExecutionLeasePolicy.evaluate(
                        ExecutionBlocker.COOPERATIVE_WORK, started, ASSIGNED_AT,
                        ASSIGNED_AT, ASSIGNED_AT + held);

                assertEquals(ExecutionLeasePolicy.LeaseDecision.SUSPEND, outcome.decision(),
                        "started=" + started + " held=" + held
                                + ": gathering the ore the tunnel exposed is not a reason to "
                                + "destroy the tunnel");
            }
        }
    }

    /**
     * The pause must be real, not merely non-revoking: blocked time is excluded from the progress
     * window, so the tunnel resumes with no false elapsed time.
     */
    @Test
    void mustHappen_gatherTimeIsExcludedFromTheProgressWindow() {
        long start = 100L;
        MiningExecutionLease lease =
                MiningExecutionLease.issued(MiningProjectMode.TUNNEL_SEARCH, 0L)
                        .started(start)
                        .markProgress(start);

        long gatherBegan = start + 50;
        long gatherEnded = gatherBegan + ExecutionLeasePolicy.PROGRESS_LEASE_TICKS * 2L;

        lease = lease.recordBlocker(ExecutionBlocker.COOPERATIVE_WORK, gatherBegan);
        lease = lease.recordBlocker(ExecutionBlocker.NONE, gatherEnded);

        assertTrue(lease.progressPausedTicks() >= ExecutionLeasePolicy.PROGRESS_LEASE_TICKS,
                "the whole gather episode must be credited back to the progress window");
        assertTrue(ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, lease, gatherEnded)
                        .authorized(),
                "the tunnel resumes immediately after a long, entirely productive gather - it must "
                        + "not be revoked for time it spent being served");
    }

    /** Without the pause the same timeline destroys the project. Documents the defect's shape. */
    @Test
    void mustNotHappen_theSameTimelineWithoutAPauseLooksHealthy() {
        long start = 100L;
        MiningExecutionLease unpaused =
                MiningExecutionLease.issued(MiningProjectMode.TUNNEL_SEARCH, 0L)
                        .started(start)
                        .markProgress(start);

        long longGather = start + ExecutionLeasePolicy.PROGRESS_LEASE_TICKS * 2L;

        assertSame(MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, unpaused, longGather)
                        .revokeReason(),
                "this is what reporting cooperative work as blocker NONE produces");
    }

    // ---- classification: three cases, not two ----

    @Test
    void mustHappen_aCooperativeHolderIsNotReportedAsNoBlocker() {
        assertSame(ExecutionBlocker.COOPERATIVE_WORK,
                MoveHolderClassifier.leaseBlocker(
                        MoveHolderClassification.COOPERATIVE_PROJECT_WORK));
        assertNotEquals(ExecutionBlocker.NONE,
                MoveHolderClassifier.leaseBlocker(
                        MoveHolderClassification.COOPERATIVE_PROJECT_WORK),
                "a goal that reached classification by holding MOVE is never 'nothing in the way'");
    }

    @Test
    void mustNotHappen_miningForcesItsOwnConsumerToYield() {
        for (ExecutionIntent intent : ExecutionIntent.values()) {
            assertFalse(MoveHolderClassifier.blocksMiningExecution(
                            intent, MoveHolderClassification.COOPERATIVE_PROJECT_WORK),
                    intent + ": preempting the consumer that completes the project's purpose "
                            + "would invert the mode");
        }
    }

    /**
     * The distinction that makes the classifier correct: the project's own executor holding its own
     * flags is not a blocker, while a different participating goal doing the project's work is a
     * pause. Collapsing these is what produced the defect.
     */
    @Test
    void mustHappen_designatedConsumerAndCooperativeHelperAreDistinguished() {
        assertTrue(MiningGoalKind.CONTROLLED_DESCENT.isDesignatedConsumer());
        assertTrue(MiningGoalKind.EXPLORING_CAVE_HANDOFF.isDesignatedConsumer());
        assertFalse(MiningGoalKind.GATHER_RESOURCES.isDesignatedConsumer(),
                "gather is a consumer of exposure, never the executor of a mining project");

        assertSame(ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CAVE_HANDOFF, MiningGoalKind.EXPLORING_CAVE_HANDOFF),
                "designated consumer of its own intent - holding MOVE here is normal execution");
    }

    /**
     * Staging is deliberate and recorded: no intent currently returns {@code ALLOW} for a
     * non-designated kind, so this classification is unreachable until the {@code TUNNEL_SEARCH}
     * arbitration row lands. The contract exists first because the executor cannot be written
     * correctly without it — but nothing here claims the path is live.
     */
    @Test
    void documentsThatTheCooperativePathIsNotYetReachable() {
        for (ExecutionIntent intent : ExecutionIntent.values()) {
            for (MiningGoalKind kind : MiningGoalKind.values()) {
                if (kind.isDesignatedConsumer()) {
                    continue;
                }
                assertNotEquals(ArbitrationDecision.ALLOW,
                        MiningExecutionArbiter.decide(intent, kind),
                        "when this starts failing, TUNNEL_SEARCH has landed and the cooperative "
                                + "path is live - update this test rather than deleting it");
            }
        }
    }
}
