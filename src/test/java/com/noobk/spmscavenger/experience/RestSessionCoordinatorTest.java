package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestSessionCoordinatorTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
    }

    @Test
    void liveRestClaimClassifiesObserverAsResting() {
        RestSessionClaim claim = new RestSessionClaim(
                UUID.randomUUID(),
                Optional.empty(),
                UUID.randomUUID(),
                RestSourceKind.DISCRETIONARY_REST,
                new BlockPos(1, 64, 2),
                RestAnchorType.CAMPFIRE,
                0L,
                0L,
                0L,
                Optional.empty());
        OpinionExperienceRegistry.contextFor(MOB).setRestClaim(Optional.of(claim));

        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.IDLE_CANDIDATE, ActivityClass.PASSIVE_COSMETIC),
                true);

        assertTrue(observation.resting());
        assertFalse(observation.discretionaryIdleCandidate());
    }

    @Test
    void unloadInvalidatesEphemeralRestClaim() {
        RestSessionClaim claim = new RestSessionClaim(
                UUID.randomUUID(),
                Optional.empty(),
                UUID.randomUUID(),
                RestSourceKind.DISCRETIONARY_REST,
                BlockPos.ZERO,
                RestAnchorType.CAMPFIRE,
                0L,
                0L,
                0L,
                Optional.empty());
        OpinionExperienceRegistry.contextFor(MOB).setRestClaim(Optional.of(claim));

        RestSessionCoordinator.invalidateOnUnload(MOB, 50L);

        assertFalse(OpinionExperienceRegistry.contextFor(MOB).hasLiveRestClaim());
    }

    @Test
    void suspendedShelterClaimRemainsLiveButDoesNotReportActiveRest() {
        RestSessionClaim claim = new RestSessionClaim(
                UUID.randomUUID(), Optional.empty(), UUID.randomUUID(),
                RestSourceKind.SHELTER_RECOVERY, BlockPos.ZERO,
                RestAnchorType.SHELTER_STAND, 0L, 0L, 0L, Optional.empty());
        RestSessionClaim suspended = claim.suspended(20L);

        OpinionExperienceRegistry.contextFor(MOB).setRestClaim(Optional.of(suspended));

        assertTrue(OpinionExperienceRegistry.contextFor(MOB).hasLiveRestClaim());
        assertFalse(OpinionExperienceRegistry.contextFor(MOB).hasActiveRestClaim());
        assertTrue(suspended.resumed(30L).isActive());
        assertEquals(claim.claimId(), suspended.resumed(30L).claimId());
        assertEquals(claim.commitmentId(), suspended.resumed(30L).commitmentId());
    }
}
