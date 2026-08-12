package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorPassagePolicyTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-0000000000d0");
    private static final BlockPos DOOR = new BlockPos(10, 64, 10);

    @Test
    void onlyClosedMeaningfullyNewEncounterStartsOpenEpisode() {
        assertTrue(DoorPassagePolicy.admitOpenEpisode(false, false, true, 0));
        assertFalse(DoorPassagePolicy.admitOpenEpisode(true, false, true, 0),
                "already-open door must not animate a no-op OPEN");
        assertFalse(DoorPassagePolicy.admitOpenEpisode(false, true, true, 1),
                "completed physical encounter must not restart immediately");
        assertFalse(DoorPassagePolicy.admitOpenEpisode(false, false, false, 1),
                "side jitter must not manufacture a new traversal generation");
        assertTrue(DoorPassagePolicy.admitOpenEpisode(false, false, true, 1));
        assertFalse(DoorPassagePolicy.admitOpenEpisode(false, false, true, 2),
                "an un-crossed physical encounter has a bounded retry count");
    }

    @Test
    void closeBehindRequiresObservedPhysicalPassage() {
        assertTrue(DoorPassagePolicy.closeAfterEpisode(true));
        assertFalse(DoorPassagePolicy.closeAfterEpisode(false),
                "timer expiry in front of door must leave it open for recovery");
    }

    @Test
    void physicalIdentitySurvivesPathReplacementAndResetsOnlyAfterSeparation() {
        DoorPassagePolicy.EncounterKey west = DoorPassagePolicy.key(
                MOB, DOOR, 9.0D, 10.5D, 7);

        assertEquals(DoorPassagePolicy.ApproachSide.WEST, west.approachSide());
        assertTrue(DoorPassagePolicy.sameDoor(west, MOB, DOOR));
        assertFalse(DoorPassagePolicy.sameDoor(west, MOB, DOOR.east()));
        assertFalse(DoorPassagePolicy.separated(west, 11.0D, 10.5D),
                "crossing the threshold does not itself end the encounter");
        assertTrue(DoorPassagePolicy.separated(west, 13.1D, 10.5D),
                "clear physical departure admits a later generation");

        assertEquals(DoorPassagePolicy.ApproachSide.EAST,
                DoorPassagePolicy.approachSide(DOOR, 12.0D, 10.5D));
        assertEquals(DoorPassagePolicy.ApproachSide.NORTH,
                DoorPassagePolicy.approachSide(DOOR, 10.5D, 9.0D));
        assertEquals(DoorPassagePolicy.ApproachSide.SOUTH,
                DoorPassagePolicy.approachSide(DOOR, 10.5D, 12.0D));
    }

    @Test
    void encounterGenerationIsFixedWidthAndWrapsWithoutAllocatingHistory() {
        assertEquals(1, DoorPassagePolicy.nextGeneration(0));
        assertEquals(0, DoorPassagePolicy.nextGeneration(Integer.MAX_VALUE));
    }
}
