package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-mode MAIBS — the whole chain as one state machine, not one matrix lookup.
 *
 * <pre>
 * DESCENT → HANDOFF_TUNNEL_SEARCH → TUNNEL → EXPOSE → GATHER → VEIN → RESUME → BREAKTHROUGH
 * </pre>
 *
 * <p>Every previous test proved one hop in isolation, which is how four control-flow breaks shipped
 * together: a claim API with no caller, a yield condition that could never be reached, a bypass
 * placed behind the gate it bypasses, and an executor that could resurrect a revoked project. Each
 * component was individually correct.
 *
 * <p>These walk the transitions in order and assert what the executors <em>would</em> observe at
 * each step, so a break anywhere in the chain fails here rather than in a running game.
 */
class TunnelExecutionChainTest {

    private static final UUID MOB = UUID.nameUUIDFromBytes("chain".getBytes());
    private static final BlockPos BAND = new BlockPos(0, 12, 0);
    private static final Direction EAST = Direction.EAST;

    private static MiningTransition tunnelHandoff(long tick) {
        return new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT, MiningProjectEnd.HANDOFF_TUNNEL_SEARCH,
                BAND, EAST, BAND.east(4), tick);
    }

    private static MiningProject tunnelAt(long started) {
        return MiningProject.start(
                MiningProjectMode.TUNNEL_SEARCH, BAND, EAST,
                MiningBudget.controlledDescentDefaults(), started);
    }

    /** Cells one 1x2 corridor step opens. */
    private static List<BlockPos> cut(BlockPos stand) {
        return HorizontalStepPlanner.planStep(stand, EAST).requiredBreaks();
    }

    @Test
    void theWholeChainAdvances() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        long now = 1_000L;

        // ---- 1. descent ends at the band with a tunnel handoff ----
        MiningProject descent = MiningProject.startControlledDescent(BAND, EAST, now - 500);
        store.putProject(MOB, descent);
        store.putLease(MOB, MiningExecutionLease.issued(
                MiningProjectMode.CONTROLLED_DESCENT, now - 500));
        store.completeProject(MOB, MiningProjectEnd.HANDOFF_TUNNEL_SEARCH, tunnelHandoff(now));

        assertTrue(store.projectOf(MOB).isEmpty(), "descent released its project");
        assertSame(ExecutionIntent.TUNNEL_HANDOFF_PENDING,
                ExecutionIntentPolicy.derive(store, MOB, now),
                "observable but not actionable until something claims it");
        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, now),
                "M1: the unresolved handoff blocks a fresh staircase - so something MUST claim it, "
                        + "or the mob stops here forever");

        // ---- 2. the handoff is claimed as a real project ----
        now += 10;
        MiningProject tunnel = tunnelAt(now);
        assertTrue(store.claimTunnelSearchHandoff(MOB, tunnelHandoff(now - 10), tunnel, now)
                .isPresent());
        assertSame(ExecutionIntent.TUNNEL_SEARCH, ExecutionIntentPolicy.derive(store, MOB, now),
                "an active tunnel project is now the intent - not the transition that made it");
        assertSame(MiningProjectMode.TUNNEL_SEARCH, store.leaseOf(MOB).orElseThrow().mode());

        // The tunnel executor may run: nothing is on offer yet.
        assertFalse(ExposureOpportunityPolicy.isLive(
                        store.exposureOf(MOB).orElse(null), tunnel, now),
                "no exposure, no reason to yield");

        // ---- 3. it cuts a cell and offers what that opened ----
        now += 40;
        store.offerExposure(MOB, tunnel, cut(BAND), now);

        assertTrue(ExposureOpportunityPolicy.isLive(
                        store.exposureOf(MOB).orElse(null), tunnel, now),
                "M2: an OFFERED exposure must make the tunnel stand aside - yielding only on "
                        + "ACQUIRING is circular, because gather cannot preempt at equal priority "
                        + "and so can never reach ACQUIRING");
        assertTrue(ExposureOpportunityPolicy.offersProbe(
                store.exposureOf(MOB).orElse(null), tunnel, now));

        // ---- 4. gather takes the probe; the boundary is all it may inspect ----
        now += 1;
        ExposureOpportunity taken = store.takeExposureProbe(MOB, tunnel, now).orElseThrow();
        assertTrue(store.takeExposureProbe(MOB, tunnel, now).isEmpty(), "one probe, consumed");

        BlockPos exposedOre = BAND.east().north();
        assertTrue(ExposureOpportunityPolicy.isExposureLocal(taken, exposedOre),
                "ore in the wall the cut revealed");
        assertFalse(ExposureOpportunityPolicy.isExposureLocal(taken, BAND.east(6)),
                "and nothing further - a broad scan here would be clairvoyance");

        // ---- 5. a legitimate target opens the cooperative session ----
        assertTrue(store.beginCooperativeAcquisition(MOB, tunnel, taken, now));
        assertSame(ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.TUNNEL_SEARCH, MiningGoalKind.GATHER_RESOURCES),
                "gather is the point of the mode, not a rival");
        assertTrue(ExposureOpportunityPolicy.holdsCooperativeSession(
                        store.exposureOf(MOB).orElse(null), tunnel, now),
                "the tunnel keeps standing aside while its own work is done");

        // ---- 6. a long vein: cooperative time must not age the tunnel's lease ----
        MiningExecutionLease lease = store.leaseOf(MOB).orElseThrow()
                .started(now).markProgress(now);
        lease = lease.recordBlocker(ExecutionBlocker.COOPERATIVE_WORK, now);

        for (int ore = 0; ore < 4; ore++) {
            now += ExecutionLeasePolicy.PROGRESS_LEASE_TICKS - 1;
            assertTrue(store.noteCooperativeAcquisition(MOB, tunnel, now),
                    "ore " + ore + ": each take refreshes the vein clock");
        }
        lease = lease.recordBlocker(ExecutionBlocker.NONE, now);
        store.putLease(MOB, lease);

        assertTrue(ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, lease, now).authorized(),
                "the tunnel spent that entire time being served, not stalling - revoking it for "
                        + "NO_PROGRESS would punish the mob for succeeding");

        // ---- 7. vein exhausted: the tunnel reacquires the same project ----
        store.clearExposure(MOB);
        assertFalse(ExposureOpportunityPolicy.isLive(
                        store.exposureOf(MOB).orElse(null), tunnel, now),
                "nothing left to consume");
        assertSame(ExecutionIntent.TUNNEL_SEARCH, ExecutionIntentPolicy.derive(store, MOB, now),
                "same project, same session - not a new tunnel");
        assertTrue(store.projectOf(MOB).orElseThrow().matchesSession(tunnel));

        // ---- 8. breakthrough ends the project and hands off ----
        now += 50;
        MiningTransition breakthrough = new MiningTransition(
                MiningProjectMode.TUNNEL_SEARCH, MiningProjectEnd.CAVE_FOUND,
                BAND.east(3), EAST, BAND.east(5), now);
        store.completeProject(MOB, MiningProjectEnd.CAVE_FOUND, breakthrough);

        assertTrue(store.projectOf(MOB).isEmpty());
        assertTrue(store.exposureOf(MOB).isEmpty(),
                "terminal cleanup - an exposure has no meaning without its project");
        assertSame(ExecutionIntent.CAVE_HANDOFF, ExecutionIntentPolicy.derive(store, MOB, now),
                "and the chain continues into the cave the corridor opened");
    }

    /** M4 — a revoked project must not be resurrected by the executor that was running it. */
    @Test
    void mustNotHappen_aRevokedTunnelIsResurrectedByItsExecutor() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        long now = 2_000L;
        MiningProject tunnel = tunnelAt(now);
        store.putProject(MOB, tunnel);
        store.putLease(MOB, MiningExecutionLease.issued(MiningProjectMode.TUNNEL_SEARCH, now));

        // A hard revocation - feature disabled, world rule off, capability gone.
        store.completeProject(MOB, MiningProjectEnd.EXECUTION_UNAVAILABLE,
                MiningTransition.of(tunnel, MiningProjectEnd.EXECUTION_UNAVAILABLE, BAND, now));
        assertTrue(store.projectOf(MOB).isEmpty(), "the control plane destroyed it");

        // The goal still holds its local copy and eventually stops.
        assertFalse(MiningDirector.shouldPersistExecutorCheckpoint(store, MOB, tunnel),
                "an unguarded checkpoint would write the revoked project straight back, and the "
                        + "director would then refuse every future assignment because one exists");
    }

    /** M4 — a stale local copy must also stop executing, not merely fail to persist. */
    @Test
    void mustNotHappen_aStaleLocalProjectKeepsExecuting() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        long now = 3_000L;
        MiningProject held = tunnelAt(now);
        store.putProject(MOB, held);

        // NO_PROGRESS maps to RETRY, which shouldPersist() KEEPS - so the record survives.
        store.completeProject(MOB, MiningProjectEnd.NO_PROGRESS,
                MiningTransition.of(held, MiningProjectEnd.NO_PROGRESS, BAND, now));
        assertTrue(store.projectOf(MOB).isPresent(), "precondition: the record is still stored");

        assertTrue(MiningDirector.assignedProject(
                        store, MOB, MiningProjectMode.TUNNEL_SEARCH).isEmpty(),
                "M5: a stored non-RUNNING project is not an assignment. Nothing resumes one - "
                        + "resumable() has zero consumers - so adopting it made the executor start, "
                        + "plan and stop against a dead record every tick");
        assertFalse(MiningDirector.shouldPersistExecutorCheckpoint(store, MOB, held),
                "and the stale local copy cannot be written back over it");
    }

    /** A tunnel and a descent must never both hold authority. */
    @Test
    void mustNotHappen_bothExcavationModesRunTogether() {
        assertSame(ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.TUNNEL_SEARCH, MiningGoalKind.CONTROLLED_DESCENT));
        assertSame(ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CONTROLLED_DESCENT, MiningGoalKind.TUNNEL_SEARCH));
    }

    /**
     * M5 — one progress-lease revocation must not end mining permanently.
     *
     * <p>{@code completeProject} keeps RUNNING/INTERRUPTED/RETRY, so a {@code NO_PROGRESS}
     * revocation left the record stored. Both {@code mayStartControlledDescent} and
     * {@code claimTunnelSearch} refuse while {@code projectOf} is present, and the lease observer
     * skipped it for not being active — so nothing could ever remove it. Loop A returning through
     * the persistence rule rather than the lease.
     */
    @Test
    void mustNotHappen_aRetiredProjectBlocksEveryFutureAssignment() {
        MiningProjectSavedData store = new MiningProjectSavedData();
        long now = 4_000L;
        MiningProject tunnel = tunnelAt(now);
        store.putProject(MOB, tunnel);

        store.completeProject(MOB, MiningProjectEnd.NO_PROGRESS,
                MiningTransition.of(tunnel, MiningProjectEnd.NO_PROGRESS, BAND, now));

        assertTrue(store.projectOf(MOB).isPresent(), "RETRY persists");
        assertFalse(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, now + 1),
                "and while it is stored, no new work may be assigned - which is why something "
                        + "must retire it");

        // What the observer now does for a non-resumable stored project.
        store.clearProject(MOB);

        assertTrue(store.projectOf(MOB).isEmpty());
        assertTrue(MiningDirector.mayStartControlledDescent(
                        store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, now + 2),
                "mining resumes rather than being permanently dead after one stall");
    }
}
