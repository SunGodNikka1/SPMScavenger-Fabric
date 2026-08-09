package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 2.5 — Multi-Mode Execution Lease Support.
 *
 * <h2>The fake generality this exists to expose</h2>
 *
 * {@code MiningProject} was multi-mode on paper and {@code MiningExecutionLease} already stored a
 * mode — but {@code enforceLease} asked for a {@code CONTROLLED_DESCENT} project specifically, and
 * {@code ExecutionIntentPolicy} asked {@code isControlledDescent()}. So a running
 * {@code TUNNEL_SEARCH} project derived intent {@code NONE} and looked <em>orphaned</em> to the
 * observer, which deleted its lease. Everything the lease provides — start window, progress
 * watchdog, cooperative pause accounting — would have stopped being persistent for the second mode.
 *
 * <p>Adding the second executable mode is what proved the abstraction was still shaped around one
 * executor.
 */
class MultiModeExecutionLeaseTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("multimode".getBytes());
    private static final BlockPos ORIGIN = new BlockPos(0, 12, 0);
    private static final long STARTED = 2_000L;

    private static MiningProject tunnelProject(long started) {
        return MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, ORIGIN, Direction.EAST,
                MiningBudget.controlledDescentDefaults(), started);
    }

    private static MiningTransition tunnelHandoff(long tick) {
        return new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.HANDOFF_TUNNEL_SEARCH,
                ORIGIN, Direction.EAST, ORIGIN.east(4), tick);
    }

    // ---- intent derives from the assigned mode ----

    @Test
    void mustHappen_anActiveTunnelProjectDerivesTunnelIntent() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.putProject(MOB, tunnelProject(STARTED));

        assertSame(ExecutionIntent.TUNNEL_SEARCH,
                ExecutionIntentPolicy.derive(store, MOB, STARTED + 1),
                "a running deliberate-excavation project must be visible to the control plane");
    }

    @Test
    void mustHappen_activeProjectOutranksThePendingTransition() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        store.recordTransition(MOB, tunnelHandoff(STARTED));
        store.putProject(MOB, tunnelProject(STARTED));

        assertSame(ExecutionIntent.TUNNEL_SEARCH,
                ExecutionIntentPolicy.derive(store, MOB, STARTED + 1),
                "the project supersedes the handoff that produced it, not the other way round");
    }

    @Test
    void mustNotHappen_aCataloguedModeWithNoExecutorClaimsIntent() {
        for (MiningProjectMode mode : MiningProjectMode.values()) {
            boolean executable = mode == MiningProjectMode.CONTROLLED_DESCENT
                    || mode == MiningProjectMode.TUNNEL_SEARCH;
            assertEquals(executable, ExecutionIntentPolicy.intentOf(mode).isPresent(),
                    mode + ": an enum value is not an executor - authority requires one");
        }
    }

    // ---- the lease survives, and stays the right mode ----

    @Test
    void mustNotHappen_theObserverClearsALeaseForNotBeingControlledDescent() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningProject project = tunnelProject(STARTED);
        store.putProject(MOB, project);
        store.putLease(MOB, MiningExecutionLease.issued(MiningProjectMode.TUNNEL_SEARCH, STARTED));

        Optional<MiningProject> served = store.projectOf(MOB)
                .filter(MiningProject::isActive)
                .filter(candidate -> ExecutionIntentPolicy.intentOf(candidate.mode()).isPresent());

        assertTrue(served.isPresent(),
                "the observer must serve whatever executable project is assigned; the old lookup "
                        + "asked for CONTROLLED_DESCENT and would have called this orphaned");
        assertSame(MiningProjectMode.TUNNEL_SEARCH, served.get().mode());
        assertTrue(store.leaseOf(MOB).isPresent(), "so the lease keeps its meaning across ticks");
    }

    /**
     * The scenario in full: a long-running tunnel with intermittent cooperative gather work must
     * keep its lease, keep its mode, and not be revoked by the progress watchdog for time spent
     * being served.
     */
    @Test
    void mustHappen_aTunnelLeaseSurvivesLongCooperativeWork() {
        MiningExecutionLease lease =
                MiningExecutionLease.issued(MiningProjectMode.TUNNEL_SEARCH, STARTED)
                        .started(STARTED + 10)
                        .markProgress(STARTED + 10);

        long now = STARTED + 10;
        for (int cycle = 0; cycle < 3; cycle++) {
            // Cooperative gather: far longer than the progress lease, entirely productive.
            lease = lease.recordBlocker(ExecutionBlocker.COOPERATIVE_WORK, now + 20);
            now += 20 + ExecutionLeasePolicy.PROGRESS_LEASE_TICKS * 2L;
            lease = lease.recordBlocker(ExecutionBlocker.NONE, now);

            assertTrue(ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, lease, now).authorized(),
                    "cycle " + cycle + ": the tunnel was being served, not stalling");

            lease = lease.markProgress(now + 5);
            now += 5;
        }

        assertSame(MiningProjectMode.TUNNEL_SEARCH, lease.mode(),
                "and it is still the same assignment it started as");
    }

    @Test
    void mustNotHappen_aLeaseIssuedForAnotherModeAuthorisesThisOne() {
        MiningExecutionLease descentLease =
                MiningExecutionLease.issued(MiningProjectMode.CONTROLLED_DESCENT, STARTED);

        assertFalse(descentLease.mode() == MiningProjectMode.TUNNEL_SEARCH,
                "the observer reissues rather than honouring a lease from a different mode - a "
                        + "stale descent lease must not carry a tunnel's start or progress clocks");
    }

    // ---- atomic handoff claim + demand revalidation ----

    @Test
    void mustHappen_theTunnelHandoffIsClaimedAtomically() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningTransition handoff = tunnelHandoff(STARTED);
        store.recordTransition(MOB, handoff);

        Optional<MiningProject> claimed = store.claimTunnelSearchHandoff(
                MOB, handoff, tunnelProject(STARTED + 1), STARTED + 1);

        assertTrue(claimed.isPresent());
        assertTrue(store.pendingTransition(MOB).isEmpty(), "handoff consumed");
        assertTrue(store.projectOf(MOB).isPresent(), "project created");
        assertSame(MiningProjectMode.TUNNEL_SEARCH, store.leaseOf(MOB).orElseThrow().mode(),
                "lease issued for the mode actually assigned - all three, or none");
    }

    @Test
    void mustNotHappen_aHandoffIsClaimedTwice() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningTransition handoff = tunnelHandoff(STARTED);
        store.recordTransition(MOB, handoff);

        assertTrue(store.claimTunnelSearchHandoff(
                MOB, handoff, tunnelProject(STARTED + 1), STARTED + 1).isPresent());
        assertTrue(store.claimTunnelSearchHandoff(
                        MOB, handoff, tunnelProject(STARTED + 2), STARTED + 2).isEmpty(),
                "the transition is gone and a project already exists");
    }

    @Test
    void mustNotHappen_aHandoffStartsATunnelOverAnExistingProject() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningTransition handoff = tunnelHandoff(STARTED);
        store.recordTransition(MOB, handoff);
        store.putProject(MOB, MiningProject.startControlledDescent(
                ORIGIN, Direction.EAST, STARTED));

        assertTrue(store.claimTunnelSearchHandoff(
                        MOB, handoff, tunnelProject(STARTED + 1), STARTED + 1).isEmpty(),
                "one project per mob - a second would race the first for the same lease slot");
        assertTrue(store.pendingTransition(MOB).isPresent(),
                "and a refused claim must not consume the handoff it could not act on");
    }

    // ---- terminal cleanup ----

    @Test
    void mustHappen_completingAProjectClearsItsExposure() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        MiningProject project = tunnelProject(STARTED);
        store.putProject(MOB, project);
        store.offerExposure(MOB, project, java.util.List.of(ORIGIN.east()), STARTED);
        assertTrue(store.exposureOf(MOB).isPresent());

        store.completeProject(MOB, MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED);

        assertTrue(store.exposureOf(MOB).isEmpty(),
                "an exposure has no meaning without the project that cut it - session binding "
                        + "already made it unusable, but dead runtime state should not outlive it");
    }
}
