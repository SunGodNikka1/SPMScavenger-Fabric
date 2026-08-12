package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorPassagePolicyTest {

    @Test
    void onlyClosedMeaningfullyNewEncounterStartsOpenEpisode() {
        assertTrue(DoorPassagePolicy.admitOpenEpisode(false, false));
        assertFalse(DoorPassagePolicy.admitOpenEpisode(true, false),
                "already-open door must not animate a no-op OPEN");
        assertFalse(DoorPassagePolicy.admitOpenEpisode(false, true),
                "same unchanged door/path encounter must not restart immediately");
    }

    @Test
    void closeBehindRequiresObservedPhysicalPassage() {
        assertTrue(DoorPassagePolicy.closeAfterEpisode(true));
        assertFalse(DoorPassagePolicy.closeAfterEpisode(false),
                "timer expiry in front of door must leave it open for recovery");
    }

    @Test
    void closeBehindDoesNotTurnACompletedPassageIntoANewEncounter() {
        assertTrue(DoorPassagePolicy.unchangedEncounter(true, true, true),
                "the expected close-behind must not permit immediate reopen");
        assertTrue(DoorPassagePolicy.unchangedEncounter(true, false, false));
        assertFalse(DoorPassagePolicy.unchangedEncounter(true, false, true),
                "external closure after an aborted open passage may receive a bounded retry");
        assertFalse(DoorPassagePolicy.unchangedEncounter(false, true, false),
                "different path or door is a new episode");
    }
}
