package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterCommitmentTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000051");

    @Test
    void doorSuspensionKeepsDestinationClaimAndBudgets() {
        BlockPos destination = new BlockPos(8, 64, 2);
        ShelterCommitment commitment = new ShelterCommitment(destination, destination, MOB, 100L);

        commitment.activate();
        for (int i = 0; i < 37; i++) {
            commitment.recordActiveApproachTick();
        }
        commitment.recordPathFailure();
        commitment.suspend();
        commitment.activate();

        assertEquals(destination, commitment.destination());
        assertEquals(destination, commitment.bedPos().orElseThrow());
        assertEquals(MOB, commitment.claimant());
        assertEquals(37, commitment.activeApproachTicks());
        assertEquals(1, commitment.pathFailureCount());
        assertEquals(1, commitment.resumeAttempts());
        assertEquals(ShelterCommitment.State.ACTIVE, commitment.state());
    }

    @Test
    void temporarySuspensionDoesNotSpendActiveBudgetButWallClockStillBoundsMission() {
        ShelterCommitment commitment = new ShelterCommitment(BlockPos.ZERO, null, MOB, 100L);
        commitment.activate();
        commitment.recordActiveApproachTick();
        commitment.suspend();

        assertFalse(commitment.approachBudgetExhausted(699L));
        assertTrue(commitment.approachBudgetExhausted(700L));
        assertEquals(1, commitment.activeApproachTicks());
    }

    @Test
    void activeAndPathFailureBudgetsAreNotResetByResume() {
        ShelterCommitment active = new ShelterCommitment(BlockPos.ZERO, null, MOB, 0L);
        active.activate();
        for (int i = 0; i < ShelterCommitment.MAX_ACTIVE_APPROACH_TICKS; i++) {
            active.recordActiveApproachTick();
        }
        active.suspend();
        active.activate();
        assertTrue(active.approachBudgetExhausted(100L));

        ShelterCommitment failed = new ShelterCommitment(BlockPos.ZERO, null, MOB, 0L);
        failed.activate();
        for (int i = 0; i < ShelterCommitment.MAX_PATH_FAILURES; i++) {
            failed.recordPathFailure();
        }
        failed.suspend();
        failed.activate();
        assertTrue(failed.approachBudgetExhausted(10L));
    }

    @Test
    void arrivalEndsApproachBudgetWithoutErasingHistory() {
        ShelterCommitment commitment = new ShelterCommitment(BlockPos.ZERO, null, MOB, 0L);
        commitment.activate();
        commitment.recordPathFailure();
        commitment.arrive();
        commitment.markRestClaimOpened();

        assertFalse(commitment.approachBudgetExhausted(10_000L));
        assertEquals(1, commitment.pathFailureCount());
        assertTrue(commitment.restClaimOpened());
        assertEquals(ShelterCommitment.State.ARRIVED, commitment.state());
    }
}
