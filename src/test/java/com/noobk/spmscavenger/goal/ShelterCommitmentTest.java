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
        UUID commitmentId = UUID.fromString("00000000-0000-0000-0000-000000000052");
        ShelterCommitment commitment = new ShelterCommitment(
                commitmentId,
                destination,
                destination,
                ShelterSelectionPolicy.Tier.USABLE_BED,
                MOB,
                100L);

        commitment.activate();
        for (int i = 0; i < 37; i++) {
            commitment.recordActiveApproachTick();
        }
        commitment.recordPathFailure();
        commitment.suspend();
        commitment.activate();

        assertEquals(destination, commitment.destination());
        assertEquals(destination, commitment.bedPos().orElseThrow());
        assertEquals(commitmentId, commitment.commitmentId());
        assertEquals(ShelterSelectionPolicy.Tier.USABLE_BED, commitment.shelterTier());
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

    @Test
    void displacedArrivalUsesFreshBoundedReturnBudgetAndCanArriveAgain() {
        ShelterCommitment commitment = new ShelterCommitment(BlockPos.ZERO, null, MOB, 0L);
        commitment.activate();
        for (int i = 0; i < 350; i++) {
            commitment.recordActiveApproachTick();
        }
        commitment.arrive();

        commitment.beginReturning(2_000L);
        commitment.suspend();
        commitment.activate();
        for (int i = 0; i < 399; i++) {
            commitment.recordActiveApproachTick();
        }

        assertEquals(ShelterCommitment.State.RETURNING, commitment.state());
        assertFalse(commitment.approachBudgetExhausted(2_399L));
        commitment.recordActiveApproachTick();
        assertTrue(commitment.approachBudgetExhausted(2_400L));

        commitment.arrive();
        assertEquals(ShelterCommitment.State.ARRIVED, commitment.state());
        assertFalse(commitment.approachBudgetExhausted(20_000L));
    }

    /**
     * V2-F — trade must not displace committed night shelter.
     *
     * <p>Not a new rule: the {@code default} branch already blocks every displacing activity that is
     * not mandatory. Pinned because adding an enum value is exactly the moment a default branch
     * silently acquires a new member, and nothing would have told us which way it fell.
     */
    @Test
    void tradeDoesNotDisplaceCommittedShelter() {
        assertEquals(ShelterInterruptionPolicy.Decision.BLOCK_WHILE_SHELTERED,
                ShelterInterruptionPolicy.decideCandidate(
                        com.noobk.spmscavenger.activity.ActivityClass.VILLAGE_TRADE, true));
        assertEquals(ShelterInterruptionPolicy.Decision.ALLOW_IN_PLACE,
                ShelterInterruptionPolicy.decideCandidate(
                        com.noobk.spmscavenger.activity.ActivityClass.VILLAGE_TRADE, false));
    }
}
