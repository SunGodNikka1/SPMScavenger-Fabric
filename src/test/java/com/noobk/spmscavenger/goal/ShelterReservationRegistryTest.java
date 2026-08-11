package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterReservationRegistryTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000061");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000062");
    private static final UUID C1 = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID C2 = UUID.fromString("00000000-0000-0000-0000-000000000072");

    @AfterEach
    void clear() {
        ShelterReservationRegistry.shutdownServerState();
    }

    @Test
    void spacingPreventsPilesButAllowsCapacityAndOtherDimensions() {
        assertTrue(ShelterReservationRegistry.reserve(
                A, C1, Level.OVERWORLD, BlockPos.ZERO, 1.5, 0));
        assertFalse(ShelterReservationRegistry.available(
                B, Level.OVERWORLD, new BlockPos(1, 0, 0), 1.5, 0));
        assertTrue(ShelterReservationRegistry.available(
                B, Level.OVERWORLD, new BlockPos(2, 0, 0), 1.5, 0));
        assertTrue(ShelterReservationRegistry.available(
                B, Level.NETHER, BlockPos.ZERO, 1.5, 0));
    }

    @Test
    void oldCommitmentCannotReleaseNewerReservationForSameMob() {
        assertTrue(ShelterReservationRegistry.reserve(
                A, C1, Level.OVERWORLD, BlockPos.ZERO, 1.5, 0));
        assertTrue(ShelterReservationRegistry.reserve(
                A, C2, Level.OVERWORLD, new BlockPos(4, 0, 0), 1.5, 1));

        ShelterReservationRegistry.release(A, C1);

        assertEquals(C2, ShelterReservationRegistry.reservationFor(A).commitmentId());
    }

    @Test
    void ownershipRefreshIsConditionalAndExpiryIsPhysical() {
        BlockPos site = new BlockPos(4, 64, 7);
        assertTrue(ShelterReservationRegistry.reserve(
                A, C1, Level.OVERWORLD, site, 1.5, 0));
        assertFalse(ShelterReservationRegistry.ownsAndRefresh(
                A, C2, GlobalPos.of(Level.OVERWORLD, site), 450));
        assertTrue(ShelterReservationRegistry.ownsAndRefresh(
                A, C1, GlobalPos.of(Level.OVERWORLD, site), 450));
        assertEquals(1050, ShelterReservationRegistry.reservationFor(A).expiresAt());

        assertTrue(ShelterReservationRegistry.available(
                B, Level.OVERWORLD, site, 1.5, 1051));
        assertNull(ShelterReservationRegistry.reservationFor(A));
        assertEquals(0, ShelterReservationRegistry.size());
    }

    @Test
    void ownerRemovalAndServerStopPhysicallyEvictReservations() {
        ShelterReservationRegistry.reserve(A, C1, Level.OVERWORLD, BlockPos.ZERO, 1.5, 0);
        ShelterReservationRegistry.reserve(B, C2, Level.OVERWORLD, new BlockPos(4, 0, 0), 1.5, 0);

        ShelterReservationRegistry.releaseOwner(A);
        assertNull(ShelterReservationRegistry.reservationFor(A));
        assertEquals(1, ShelterReservationRegistry.size());

        ShelterReservationRegistry.shutdownServerState();
        assertEquals(0, ShelterReservationRegistry.size());
    }
}
