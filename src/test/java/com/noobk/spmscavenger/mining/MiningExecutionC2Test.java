package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import com.noobk.spmscavenger.goal.ExploringGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14C2 falsification scenarios C2-A…G at the policy layer.
 */
class MiningExecutionC2Test {

    private static final UUID MOB = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void c2a_gatherYieldsOnCaveHandoffExploreAllowed() {
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CAVE_HANDOFF, MiningGoalKind.GATHER_RESOURCES));
        assertEquals(
                ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CAVE_HANDOFF, MiningGoalKind.EXPLORING_CAVE_HANDOFF));
    }

    @Test
    void c2b_smeltYieldsOnControlledDescentDescentAllowed() {
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CONTROLLED_DESCENT, MiningGoalKind.SMELT_AT_FURNACE));
        assertEquals(
                ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.CONTROLLED_DESCENT, MiningGoalKind.CONTROLLED_DESCENT));
    }

    @Test
    void c2c_combatRemainsTemporaryNotContention() {
        assertEquals(
                ExecutionBlocker.BlockerClass.TEMPORARY,
                ExecutionBlocker.COMBAT_TARGET.blockerClass());
        assertEquals(
                ExecutionBlocker.BlockerClass.CONTENTION,
                ExecutionBlocker.CONTENTION.blockerClass());
        assertFalse(ExecutionBlocker.COMBAT_TARGET == ExecutionBlocker.CONTENTION);
    }

    @Test
    void c2d_combatSuspensionDoesNotRevokeHealthyProjectImmediately() {
        long combatAt = 6_000L;
        var outcome = ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.COMBAT_TARGET, true, 0L, combatAt, combatAt);
        assertEquals(ExecutionLeasePolicy.LeaseDecision.SUSPEND, outcome.decision());
    }

    @Test
    void c2e_tunnelHandoffPendingIsNeutralAndDoesNotConsumeTransition() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition tunnel = new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.HANDOFF_TUNNEL_SEARCH,
                BlockPos.ZERO,
                Direction.NORTH,
                BlockPos.ZERO,
                100L);
        store.recordTransition(MOB, tunnel);

        assertEquals(
                ExecutionIntent.TUNNEL_HANDOFF_PENDING,
                ExecutionIntentPolicy.derive(store, MOB, 200L));
        assertEquals(
                ArbitrationDecision.NEUTRAL,
                MiningExecutionArbiter.decide(
                        ExecutionIntent.TUNNEL_HANDOFF_PENDING, MiningGoalKind.GATHER_RESOURCES));
        assertTrue(store.pendingTransition(MOB).isPresent(), "transition must remain for Loop D");
    }

    @Test
    void c2f_contentionUsesStartLeaseWhenNeverStarted() {
        long past = ExecutionLeasePolicy.START_LEASE_TICKS + 1;
        var outcome = ExecutionLeasePolicy.evaluate(
                ExecutionBlocker.CONTENTION, false, 1_000L, MiningExecutionLease.NOT_BLOCKED, 1_000L + past);
        assertTrue(outcome.revoked());
        assertEquals(MiningProjectEnd.LEASE_EXPIRED, outcome.revokeReason());
    }

    @Test
    void c2g_intentChangeYieldsGatherAfterHandoffClaimed() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition handoff = new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.CAVE_FOUND,
                new BlockPos(1, 32, 2),
                Direction.SOUTH,
                new BlockPos(1, 30, 2),
                500L);
        store.recordTransition(MOB, handoff);
        assertTrue(store.claimCaveContinuation(MOB, handoff, 600L, ExploringGoal.MAX_EXPEDITION_TICKS));

        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, MOB, 601L);
        assertEquals(ExecutionIntent.CAVE_HANDOFF, intent);
        assertTrue(store.pendingTransition(MOB).isEmpty());
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(intent, MiningGoalKind.GATHER_RESOURCES));
        assertEquals(
                ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(intent, MiningGoalKind.EXPLORING_CAVE_HANDOFF));
    }

    @Test
    void derivePrefersActiveProjectOverPendingTransition() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        store.putProject(MOB, MiningProject.startControlledDescent(BlockPos.ZERO, Direction.EAST, 0L));
        store.recordTransition(
                MOB,
                new MiningTransition(
                        MiningProjectMode.CONTROLLED_DESCENT,
                        MiningProjectEnd.CAVE_FOUND,
                        BlockPos.ZERO,
                        Direction.NORTH,
                        BlockPos.ZERO,
                        0L));

        assertEquals(
                ExecutionIntent.CONTROLLED_DESCENT, ExecutionIntentPolicy.derive(store, MOB, 0L));
    }

    @Test
    void searchBudgetExhaustedPendingDoesNotCreateActionableIntent() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        store.recordTransition(
                MOB,
                new MiningTransition(
                        MiningProjectMode.CONTROLLED_DESCENT,
                        MiningProjectEnd.SEARCH_BUDGET_EXHAUSTED,
                        BlockPos.ZERO,
                        Direction.NORTH,
                        BlockPos.ZERO,
                        0L));

        assertEquals(ExecutionIntent.NONE, ExecutionIntentPolicy.derive(store, MOB, 0L));
        assertEquals(
                ArbitrationDecision.NEUTRAL,
                MiningExecutionArbiter.decide(ExecutionIntent.NONE, MiningGoalKind.GATHER_RESOURCES));
    }
}
