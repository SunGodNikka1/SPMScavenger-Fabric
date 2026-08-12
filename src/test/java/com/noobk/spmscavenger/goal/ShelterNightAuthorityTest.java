package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShelterNightAuthorityTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID OLD = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final UUID CURRENT = UUID.fromString("00000000-0000-0000-0000-000000000093");
    private static final BlockPos ANCHOR = new BlockPos(4, 64, 7);

    @AfterEach
    void clearRegistry() {
        ShelterNightAuthority.clear();
    }

    @Test
    void exactArrivalStoresBoundedCorrelatedSnapshot() {
        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR, 42L);

        assertTrue(ShelterNightAuthority.holds(MOB));
        ShelterNightAuthority.Hold hold = ShelterNightAuthority.hold(MOB).orElseThrow();
        assertEquals(CURRENT, hold.commitmentId());
        assertEquals(ANCHOR, hold.anchor());
        assertEquals(42L, hold.arrivedAt());
        assertEquals(ShelterNightAuthority.Phase.SETTLED, hold.phase());
        assertTrue(ShelterNightAuthority.isSettled(MOB));
        assertEquals(1, ShelterNightAuthority.size());
    }

    @Test
    void returningRetainsNightAuthorityButReleasesFiniteDoorWrapper() {
        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR, 42L);

        ShelterNightAuthority.markReturning(MOB, CURRENT);

        assertTrue(ShelterNightAuthority.holds(MOB));
        assertFalse(ShelterNightAuthority.isSettled(MOB));
        assertEquals(ShelterNightAuthority.Phase.RETURNING,
                ShelterNightAuthority.hold(MOB).orElseThrow().phase());

        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR, 55L);
        assertTrue(ShelterNightAuthority.isSettled(MOB));
    }

    @Test
    void obsoleteCommitmentCannotReleaseNewerHold() {
        ShelterNightAuthority.acquire(MOB, OLD, ANCHOR, 10L);
        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR.offset(1, 0, 0), 20L);

        ShelterNightAuthority.release(MOB, OLD);

        assertTrue(ShelterNightAuthority.holds(MOB));
        assertEquals(CURRENT, ShelterNightAuthority.hold(MOB).orElseThrow().commitmentId());

        ShelterNightAuthority.release(MOB, CURRENT);
        assertFalse(ShelterNightAuthority.holds(MOB));
    }

    @Test
    void ownerRemovalAndServerStopAreProductionBounds() {
        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR, 42L);
        ShelterNightAuthority.releaseOwner(MOB);
        assertEquals(0, ShelterNightAuthority.size());

        ShelterNightAuthority.acquire(MOB, CURRENT, ANCHOR, 42L);
        ShelterNightAuthority.clear();
        assertEquals(0, ShelterNightAuthority.size());
    }
}
