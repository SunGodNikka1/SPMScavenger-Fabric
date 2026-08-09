package com.noobk.spmscavenger.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MI-14C3-R1 cross-layer falsification scenarios C3-F1...F7. */
class MiningExecutionC3R1Test {

    @Test
    void c3f1_conditionBoundSafetyPauseSurvivesBeyondBothOtherTimeouts() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(10L)
                .markProgress(100L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 200L);

        assertEquals(
                ExecutionLeasePolicy.LeaseDecision.SUSPEND,
                ExecutionLeasePolicy.evaluate(ExecutionBlocker.SAFETY_RECOVERY, lease, 3_000L)
                        .decision());

        lease = lease.recordBlocker(ExecutionBlocker.NONE, 3_000L);
        assertEquals(2_800L, lease.progressPausedTicks());
        assertFalse(ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, lease, 3_300L).revoked());
        assertEquals(
                MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(ExecutionBlocker.NONE, lease, 3_301L)
                        .revokeReason());
    }

    @Test
    void c3f2_preStartSafetyPausePreservesOnlyAdmissibleStartAge() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 0L);

        assertEquals(
                ExecutionLeasePolicy.LeaseDecision.SUSPEND,
                ExecutionLeasePolicy.evaluate(ExecutionBlocker.SAFETY_RECOVERY, lease, 3_000L)
                        .decision());

        lease = lease.recordBlocker(ExecutionBlocker.CONTENTION, 3_000L);
        assertEquals(3_000L, lease.startPausedTicks());
        assertFalse(ExecutionLeasePolicy.evaluate(ExecutionBlocker.CONTENTION, lease, 3_600L).revoked());
        assertEquals(
                MiningProjectEnd.LEASE_EXPIRED,
                ExecutionLeasePolicy.evaluate(ExecutionBlocker.CONTENTION, lease, 3_601L)
                        .revokeReason());
    }

    @Test
    void c3f3_playerOrderRevokesRatherThanCreatingACommandZombie() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L);
        var outcome = ExecutionLeasePolicy.evaluate(ExecutionBlocker.PLAYER_ORDER, lease, 1L);

        assertTrue(outcome.revoked());
        assertEquals(MiningProjectEnd.PLAYER_ORDER, outcome.revokeReason());
        assertTrue(SchedulerConflictPolicy.preventsAssignment(ExecutionBlocker.PLAYER_ORDER));
        assertFalse(SchedulerConflictPolicy.preventsAssignment(ExecutionBlocker.SAFETY_RECOVERY));
    }

    @Test
    void c3f4_completedProtectedEpisodeIsSettledExactlyOnce() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(10L)
                .markProgress(20L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 100L);

        lease = lease.recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 300L);
        assertEquals(0L, lease.progressPausedTicks());
        lease = lease.recordBlocker(ExecutionBlocker.NONE, 300L);
        assertEquals(200L, lease.progressPausedTicks());
        lease = lease.recordBlocker(ExecutionBlocker.NONE, 500L);
        assertEquals(200L, lease.progressPausedTicks());
    }

    @Test
    void c3f5_protectedCombatProtectedEpisodesStaySeparateWithoutLostPause() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(10L)
                .markProgress(20L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 100L)
                .recordBlocker(ExecutionBlocker.COMBAT_TARGET, 500L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 600L)
                .recordBlocker(ExecutionBlocker.NONE, 900L);

        assertEquals(800L, lease.progressPausedTicks());
        assertEquals(ExecutionBlocker.NONE, lease.currentBlocker());
        assertEquals(MiningExecutionLease.NOT_BLOCKED, lease.blockedSince());
    }

    @Test
    void c3f6_progressLeaseCanFireWhileAbsoluteBudgetStillHasRoom() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .started(0L)
                .markProgress(0L);
        MiningProject project = MiningProject.startControlledDescent(
                BlockPos.ZERO, Direction.NORTH, 0L);
        for (int tick = 0; tick <= ExecutionLeasePolicy.PROGRESS_LEASE_TICKS; tick++) {
            project = project.withBudgetUsage(project.budgetUsage().withTick());
        }

        assertFalse(project.isBudgetExhausted(), "400-tick stall must precede 2400-tick total cap");
        assertEquals(
                MiningProjectEnd.NO_PROGRESS,
                ExecutionLeasePolicy.evaluate(
                                ExecutionBlocker.NONE,
                                lease,
                                ExecutionLeasePolicy.PROGRESS_LEASE_TICKS + 1L)
                        .revokeReason());
    }

    @Test
    void c3f7_lookOnlyEatingConflictsWithMoveLookExecutorAndPauses() {
        Goal eating = new EatFoodGoal();

        assertEquals(
                MoveHolderClassification.PROTECTED_LOW_FOOD,
                MoveHolderClassifier.classify(
                        eating,
                        null,
                        MiningProjectSavedData.createEmpty(),
                        java.util.UUID.randomUUID(),
                        0L,
                        EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
        assertEquals(
                ExecutionBlocker.LOW_FOOD,
                MoveHolderClassifier.leaseBlocker(MoveHolderClassification.PROTECTED_LOW_FOOD));
    }

    @Test
    void requiredFlagIntersectionIgnoresUnrelatedAndFlaglessGoals() {
        assertFalse(MoveHolderClassifier.conflictsWithRequiredFlags(
                EnumSet.of(Goal.Flag.JUMP), EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
        assertFalse(MoveHolderClassifier.conflictsWithRequiredFlags(
                EnumSet.noneOf(Goal.Flag.class), EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
        assertTrue(MoveHolderClassifier.conflictsWithRequiredFlags(
                EnumSet.of(Goal.Flag.LOOK), EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
    }

    @Test
    void safetyAndCommandTaxonomyIsExplicit() {
        assertEquals(MoveHolderClassification.PROTECTED_SAFETY_RECOVERY,
                classify(new FireBucketGoal()));
        assertEquals(MoveHolderClassification.PROTECTED_SAFETY_RECOVERY,
                classify(new TrainRecoveryGoal()));
        assertEquals(MoveHolderClassification.PROTECTED_PLAYER_ORDER,
                classify(new CommandedActionGoal()));
        assertEquals(MoveHolderClassification.PROTECTED_PLAYER_ORDER,
                classify(new StayNearGoal()));
    }

    @Test
    void nbtV4PreservesBothPauseClocks() {
        MiningExecutionLease lease = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 0L)
                .recordBlocker(ExecutionBlocker.SAFETY_RECOVERY, 100L)
                .recordBlocker(ExecutionBlocker.NONE, 350L);

        assertEquals(250L, lease.startPausedTicks());
        assertEquals(lease, MiningExecutionLease.load(lease.save()));
    }

    @Test
    void legacyV3LeaseMigratesWithoutInventingPreStartPause() {
        net.minecraft.nbt.CompoundTag v3 = MiningExecutionLease
                .issued(MiningProjectMode.CONTROLLED_DESCENT, 10L)
                .started(20L)
                .markProgress(30L)
                .recordBlocker(ExecutionBlocker.CONTENTION, 40L)
                .recordBlocker(ExecutionBlocker.NONE, 70L)
                .save();
        v3.remove("leaseV4");
        v3.remove("startPausedTicks");

        MiningExecutionLease migrated = MiningExecutionLease.load(v3);
        assertEquals(30L, migrated.progressPausedTicks());
        assertEquals(0L, migrated.startPausedTicks());
    }

    private static MoveHolderClassification classify(Goal goal) {
        return MoveHolderClassifier.classify(
                goal,
                null,
                MiningProjectSavedData.createEmpty(),
                java.util.UUID.randomUUID(),
                0L,
                EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private abstract static class MoveLookGoal extends Goal {
        private MoveLookGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }
    }

    private static final class EatFoodGoal extends Goal {
        private EatFoodGoal() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }
    }

    private static final class FireBucketGoal extends MoveLookGoal { }
    private static final class TrainRecoveryGoal extends MoveLookGoal { }
    private static final class CommandedActionGoal extends MoveLookGoal { }
    private static final class StayNearGoal extends MoveLookGoal { }
}
