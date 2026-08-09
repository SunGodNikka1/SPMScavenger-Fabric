package com.noobk.spmscavenger.mining;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MI-14C3 — observable-progress lease and exact blocker-clock suspension. */
class MiningExecutionC3Test {

    @Test
    void c3A_startedThenStuckRevokesForNoProgress() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(100L)
                .markProgress(200L);

        var outcome = ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, lease,
                200L + ExecutionLeasePolicy.PROGRESS_LEASE_TICKS + 1L);

        assertTrue(outcome.revoked());
        assertEquals(MiningProjectEnd.NO_PROGRESS, outcome.revokeReason());
    }

    @Test
    void c3B_temporaryInterruptionPausesRatherThanMerelySkippingEvaluation() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(100L)
                .recordBlocker(ExecutionBlocker.COMBAT_TARGET, 300L)
                .recordBlocker(ExecutionBlocker.NONE, 1_300L);

        assertEquals(1_000L, lease.progressPausedTicks());
        assertTrue(ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, lease, 1_500L).authorized(),
                "100 start + 1000 paused + 400 lease is still valid at the boundary");
        assertEquals(MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(
                        ExecutionBlocker.NONE, lease, 1_501L).revokeReason());
    }

    @Test
    void c3C_onlyObservableProgressRefreshesTheClock() {
        MiningExecutionLease started = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(100L);

        assertEquals(MiningExecutionLease.NO_PROGRESS_RECORDED,
                started.lastExecutionProgressAt(),
                "markExecutorStarted must not masquerade as physical progress");
        assertTrue(ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, started, 501L).revoked(),
                "ordinary elapsed ticks cannot refresh the progress lease");

        MiningExecutionLease progressed = started.markProgress(2_400L);
        assertEquals(2_400L, progressed.lastExecutionProgressAt());
        assertTrue(ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, progressed, 2_800L).authorized());
        assertEquals(MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(
                        ExecutionBlocker.NONE, progressed, 2_801L).revokeReason());
    }

    @Test
    void c3D_neverStartedUsesOnlyTheStartLease() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 1_000L)
                .recordBlocker(ExecutionBlocker.CONTENTION, 1_000L);

        var outcome = ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.CONTENTION, lease,
                1_000L + ExecutionLeasePolicy.START_LEASE_TICKS + 1L);

        assertEquals(MiningProjectEnd.LEASE_EXPIRED, outcome.revokeReason());
        assertFalse(lease.everStarted());
        assertEquals(MiningExecutionLease.NO_PROGRESS_RECORDED,
                lease.lastExecutionProgressAt());
    }

    @Test
    void c3E_contentionPausesTheProgressClock() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(100L)
                .recordBlocker(ExecutionBlocker.CONTENTION, 300L)
                .recordBlocker(ExecutionBlocker.NONE, 4_300L);

        assertEquals(4_000L, lease.progressPausedTicks());
        assertTrue(ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, lease, 4_500L).authorized());
        assertEquals(MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(
                        ExecutionBlocker.NONE, lease, 4_501L).revokeReason());
    }

    @Test
    void aNewProgressWindowCannotReusePauseCreditFromThePreviousWindow() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(100L)
                .recordBlocker(ExecutionBlocker.CONTENTION, 200L)
                .recordBlocker(ExecutionBlocker.NONE, 1_200L);
        assertEquals(1_000L, lease.progressPausedTicks());

        lease = lease.markProgress(1_500L);
        assertEquals(0L, lease.progressPausedTicks(),
                "a past suspension must not extend every future progress window");
        assertTrue(ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.NONE, lease, 1_900L).authorized());
        assertEquals(MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(
                        ExecutionBlocker.NONE, lease, 1_901L).revokeReason());
    }

    @Test
    void progressAndPauseStateSurviveSaveLoad() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 10L)
                .started(20L)
                .markProgress(30L)
                .recordBlocker(ExecutionBlocker.CONTENTION, 40L)
                .recordBlocker(ExecutionBlocker.NONE, 70L);

        assertEquals(lease, MiningExecutionLease.load(lease.save()));
    }

    @Test
    void legacyV2LeaseMigratesWithoutInventingProgress() {
        CompoundTag v2 = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 10L)
                .started(20L)
                .save();
        v2.remove("leaseV3");
        v2.remove("leaseV4");
        v2.remove("lastProgressAt");
        v2.remove("progressPausedTicks");
        v2.remove("startPausedTicks");

        MiningExecutionLease migrated = MiningExecutionLease.load(v2);
        assertEquals(MiningExecutionLease.NO_PROGRESS_RECORDED,
                migrated.lastExecutionProgressAt());
        assertEquals(0L, migrated.progressPausedTicks());
        assertEquals(0L, migrated.startPausedTicks());
    }
}
