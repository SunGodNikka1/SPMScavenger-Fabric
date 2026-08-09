package com.noobk.spmscavenger.mining;

import org.jetbrains.annotations.Nullable;

/**
 * MI-14C1 — pure decision layer for an assignment that is not currently executing.
 *
 * <p>No world access, no side effects: it answers "authorize, suspend, or revoke" from the blocker
 * class and lease clocks. The director performs whatever it says.
 *
 * <p><b>Invariant this exists to enforce:</b> no {@code RUNNING} {@link MiningProject} may exist
 * indefinitely without either execution progress or an explicit suspension reason.
 */
public final class ExecutionLeasePolicy {

    /**
     * How long an assignment may sit unstarted before it is released. Generous enough to survive
     * ordinary goal churn, short enough that a mob is not permanently barred from re-deciding.
     */
    public static final int START_LEASE_TICKS = 600;      // 30s

    /** How long a temporary blocker episode may persist before the assignment is released. */
    public static final int TEMPORARY_GRACE_TICKS = 1200; // 60s

    /** Admissible time a started executor may spend without observable dig progress. */
    public static final int PROGRESS_LEASE_TICKS = 2400;  // 120s

    private ExecutionLeasePolicy() {
    }

    public enum LeaseDecision {
        /** The executor may run now. */
        AUTHORIZE,
        /** Keep the assignment; do not execute this tick. */
        SUSPEND,
        /** Release the assignment so something else can be decided. */
        REVOKE
    }

    /**
     * @param decision what the director should do
     * @param revokeReason terminal reason to record, non-null exactly when {@code decision} is
     *     {@link LeaseDecision#REVOKE}
     */
    public record LeaseOutcome(LeaseDecision decision, @Nullable MiningProjectEnd revokeReason) {

        public boolean authorized() {
            return decision == LeaseDecision.AUTHORIZE;
        }

        public boolean revoked() {
            return decision == LeaseDecision.REVOKE;
        }
    }

    private static final LeaseOutcome AUTHORIZE =
            new LeaseOutcome(LeaseDecision.AUTHORIZE, null);
    private static final LeaseOutcome SUSPEND =
            new LeaseOutcome(LeaseDecision.SUSPEND, null);

    /**
     * @param blocker why execution is not happening, or {@link ExecutionBlocker#NONE}
     * @param everStarted whether the executor has ever begun this assignment
     * @param assignedAt game time the director created the assignment
     * @param blockedSince game time the current blocking episode began, or
     *     {@link MiningExecutionLease#NOT_BLOCKED}
     * @param now current game time
     */
    public static LeaseOutcome evaluate(
            ExecutionBlocker blocker,
            boolean everStarted,
            long assignedAt,
            long blockedSince,
            long now) {

        if (blocker.permitsExecution()) {
            return AUTHORIZE;
        }

        long heldSinceAssignment = Math.max(0L, now - assignedAt);

        switch (blocker.blockerClass()) {
            case HARD -> {
                // The precondition is not coming back on its own, and the systems that could
                // restore it should not be competing with an assignment nobody can execute.
                return revoke(blocker);
            }
            case TEMPORARY -> {
                // Grace measures the current blocking episode, not assignment age.
                long blockedFor = blockedSince >= 0L
                        ? Math.max(0L, now - blockedSince)
                        : 0L;
                return blockedFor > TEMPORARY_GRACE_TICKS ? revoke(blocker) : SUSPEND;
            }
            case CONTENTION -> {
                // Arbitration (MI-14C2) is what should actually resolve this. Until it exists, an
                // assignment that never once got to run is released rather than held forever.
                return !everStarted && heldSinceAssignment > START_LEASE_TICKS
                        ? revoke(blocker)
                        : SUSPEND;
            }
            default -> {
                return SUSPEND;
            }
        }
    }

    /**
     * MI-14C3 evaluation with the complete persisted lease.
     *
     * <p>The C1 blocker/start decision runs first. Progress expiry is intentionally considered only
     * when execution is admissible; TEMPORARY and CONTENTION episodes are settled into
     * {@link MiningExecutionLease#progressPausedTicks()} when they clear.
     */
    public static LeaseOutcome evaluate(
            ExecutionBlocker blocker, MiningExecutionLease lease, long now) {
        LeaseOutcome base = evaluate(
                blocker,
                lease.everStarted(),
                lease.assignedAt(),
                lease.blockedSince(),
                now);
        if (!base.authorized() || !lease.everStarted()) {
            return base;
        }

        long progressBaseline = lease.lastExecutionProgressAt()
                == MiningExecutionLease.NO_PROGRESS_RECORDED
                ? lease.executorStartedAt()
                : lease.lastExecutionProgressAt();
        long wallElapsed = Math.max(0L, now - progressBaseline);
        long admissibleElapsed = Math.max(0L, wallElapsed - lease.progressPausedTicks());
        if (admissibleElapsed > PROGRESS_LEASE_TICKS) {
            return new LeaseOutcome(LeaseDecision.REVOKE, MiningProjectEnd.NO_PROGRESS);
        }
        return base;
    }

    private static LeaseOutcome revoke(ExecutionBlocker blocker) {
        MiningProjectEnd reason = blocker.revocationReason();
        return new LeaseOutcome(
                LeaseDecision.REVOKE,
                reason == null ? MiningProjectEnd.LEASE_EXPIRED : reason);
    }
}
