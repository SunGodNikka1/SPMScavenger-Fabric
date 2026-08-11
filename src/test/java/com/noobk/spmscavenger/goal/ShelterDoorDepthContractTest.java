package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterDoorDepthContractTest {

    @Test
    void productionGoalUsesDoorDepthAndExactReservedCellArrival() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/SeekShelterGoal.java"));

        assertTrue(source.contains("doorClearance(level, pos)"));
        assertTrue(source.contains("ShelterSelectionPolicy.arrivedAtStandingSite("));
        assertFalse(source.contains("ARRIVED_SQR"));
    }
}
