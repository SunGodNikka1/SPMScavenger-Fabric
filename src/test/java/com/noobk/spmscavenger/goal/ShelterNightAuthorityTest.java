package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterNightAuthorityTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000091");

    @AfterEach
    void clearRegistry() {
        ShelterNightAuthority.clear();
    }

    @Test
    void exactArrivalOwnsNightUntilDisplacementOrCancellationReleasesIt() {
        assertFalse(ShelterNightAuthority.holds(MOB));

        ShelterNightAuthority.acquire(MOB);

        assertTrue(ShelterNightAuthority.holds(MOB));
        assertEquals(1, ShelterNightAuthority.size());

        ShelterNightAuthority.release(MOB);

        assertFalse(ShelterNightAuthority.holds(MOB));
        assertEquals(0, ShelterNightAuthority.size());
    }

    @Test
    void duplicateArrivalIsBoundedPerLoadedMobAndServerStopClearsIt() {
        ShelterNightAuthority.acquire(MOB);
        ShelterNightAuthority.acquire(MOB);
        assertEquals(1, ShelterNightAuthority.size());

        ShelterNightAuthority.clear();

        assertEquals(0, ShelterNightAuthority.size());
    }
}
