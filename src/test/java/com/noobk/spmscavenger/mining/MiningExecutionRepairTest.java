package com.noobk.spmscavenger.mining;

import net.minecraft.SharedConstants;
import com.noobk.spmscavenger.goal.ExploringGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MI-14C repair package — R1 handoff lifetime, R2 scheduler-wide contention, C1-R2 safe stop.
 */
class MiningExecutionRepairTest {

    private static final UUID MOB = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void r1_caveHandoffPersistsAfterTransitionConsumed() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition handoff = new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.CAVE_FOUND,
                new BlockPos(4, 40, 6),
                Direction.SOUTH,
                new BlockPos(4, 38, 8),
                100L);
        store.recordTransition(MOB, handoff);

        assertTrue(store.claimCaveContinuation(MOB, handoff, 110L, ExploringGoal.MAX_EXPEDITION_TICKS));
        assertTrue(store.pendingTransition(MOB).isEmpty(), "transition must be consumed");
        assertTrue(store.hasActiveCaveContinuation(MOB, 111L));

        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, MOB, 111L);
        assertEquals(ExecutionIntent.CAVE_HANDOFF, intent);
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(intent, MiningGoalKind.GATHER_RESOURCES));
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(intent, MiningGoalKind.SMELT_AT_FURNACE));
        assertEquals(
                ArbitrationDecision.YIELD,
                MiningExecutionArbiter.decide(intent, MiningGoalKind.CRAFT_TORCHES));
        assertEquals(
                ArbitrationDecision.ALLOW,
                MiningExecutionArbiter.decide(
                        intent, MiningGoalKind.classifyExploring(store, MOB, 111L)));
    }

    @Test
    void r1_commitmentClearsOnExplicitClear() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition handoff = caveHandoff(200L);
        store.recordTransition(MOB, handoff);
        store.claimCaveContinuation(MOB, handoff, 200L, ExploringGoal.MAX_EXPEDITION_TICKS);

        store.clearCommitment(MOB);

        assertEquals(ExecutionIntent.NONE, ExecutionIntentPolicy.derive(store, MOB, 201L));
    }

    @Test
    void r1_commitmentExpiresWithoutKeepingTransitionPending() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition handoff = caveHandoff(0L);
        store.recordTransition(MOB, handoff);
        store.claimCaveContinuation(MOB, handoff, 0L, ExploringGoal.MAX_EXPEDITION_TICKS);

        // MI-14C2-R2: authority runs from the claim, so expiry is the authority window - not
        // the admission window. The behaviour under test is unchanged: expiring must not
        // resurrect the consumed transition.
        long afterExpiry = ExploringGoal.MAX_EXPEDITION_TICKS + 1L;
        assertFalse(store.hasActiveCaveContinuation(MOB, afterExpiry));
        assertEquals(ExecutionIntent.NONE, ExecutionIntentPolicy.derive(store, MOB, afterExpiry));
        assertTrue(store.pendingTransition(MOB).isEmpty());
    }

    @Test
    void r1_blocksFreshControlledDescentWhileContinuationActive() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningTransition handoff = caveHandoff(50L);
        store.recordTransition(MOB, handoff);
        store.claimCaveContinuation(MOB, handoff, 50L, ExploringGoal.MAX_EXPEDITION_TICKS);

        assertFalse(MiningDirector.mayStartControlledDescent(
                store, MOB, NaturalDescentStatus.EXHAUSTED, true, true, 60L));
    }

    @Test
    void r2_unknownMoveHolderBlocksActionableIntent() {
        Goal unknownMove = new UnknownMoveGoal();
        assertEquals(
                MoveHolderClassification.UNKNOWN_MOVE_HOLDER,
                MoveHolderClassifier.classify(unknownMove, null, MiningProjectSavedData.createEmpty(), MOB, 0L));
        assertTrue(MoveHolderClassifier.blocksMiningExecution(
                ExecutionIntent.CONTROLLED_DESCENT, MoveHolderClassification.UNKNOWN_MOVE_HOLDER));
        assertFalse(MoveHolderClassifier.blocksMiningExecution(
                ExecutionIntent.CONTROLLED_DESCENT,
                MoveHolderClassification.PROTECTED_SAFETY_RECOVERY));
    }

    @Test
    void r2_followLovedOneClassifiedAsOrdinaryHostWork() {
        Goal follow = new FollowLovedOneGoal();
        assertEquals(
                MoveHolderClassification.ORDINARY_HOST_WORK,
                MoveHolderClassifier.classify(
                        follow, null, MiningProjectSavedData.createEmpty(), MOB, 0L));
        assertTrue(MoveHolderClassifier.blocksMiningExecution(
                ExecutionIntent.CONTROLLED_DESCENT, MoveHolderClassification.ORDINARY_HOST_WORK));
    }

    @Test
    void c1r2_stopMustNotResurrectDirectorRevokedProject() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningProject assigned = MiningProject.startControlledDescent(BlockPos.ZERO, Direction.EAST, 10L);
        store.putProject(MOB, assigned);

        MiningProject localCopy = assigned.withLastSafeAnchor(new BlockPos(0, 63, 0));
        MiningProject finished = assigned.complete(MiningProjectEnd.LEASE_EXPIRED);
        store.putProject(MOB, finished);
        store.clearLease(MOB);

        assertFalse(MiningDirector.shouldPersistExecutorCheckpoint(store, MOB, localCopy));
        assertTrue(store.projectOf(MOB).filter(MiningProject::isActive).isEmpty());
    }

    @Test
    void c1r2_stopMayPersistActiveMatchingSession() {
        MiningProjectSavedData store = MiningProjectSavedData.createEmpty();
        MiningProject assigned = MiningProject.startControlledDescent(BlockPos.ZERO, Direction.EAST, 10L);
        store.putProject(MOB, assigned);

        MiningProject interrupted = assigned.withLastSafeAnchor(new BlockPos(0, 63, 0));
        assertTrue(MiningDirector.shouldPersistExecutorCheckpoint(store, MOB, interrupted));
    }

    @Test
    void c1r2_matchesSessionUsesOriginAndStartedTime() {
        MiningProject a = MiningProject.startControlledDescent(new BlockPos(1, 64, 2), Direction.NORTH, 5L);
        MiningProject sameSession = a.withLastSafeAnchor(new BlockPos(1, 63, 2));
        MiningProject different = MiningProject.startControlledDescent(BlockPos.ZERO, Direction.NORTH, 5L);
        assertTrue(a.matchesSession(sameSession));
        assertFalse(a.matchesSession(different));
    }

    private static MiningTransition caveHandoff(long tick) {
        return new MiningTransition(
                MiningProjectMode.CONTROLLED_DESCENT,
                MiningProjectEnd.CAVE_FOUND,
                BlockPos.ZERO,
                Direction.NORTH,
                BlockPos.ZERO,
                tick);
    }

    private static final class UnknownMoveGoal extends Goal {
        UnknownMoveGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }
    }

    /** Class name suffix matches SPM {@code FollowLovedOneGoal} for classifier probing. */
    private static final class FollowLovedOneGoal extends Goal {
        FollowLovedOneGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
