package com.noobk.spmscavenger.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.village.ObservationQuality;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class VillageMemoryDebugCommandTest {

    @Test
    void formatAnchorUsesCommaSeparatedCoords() {
        assertEquals("143, 64, -382", VillageMemoryDebugCommand.formatAnchor(new BlockPos(143, 64, -382)));
    }

    @Test
    void formatCoverageFullIsOneHundredPercent() {
        assertEquals("100%", VillageMemoryDebugCommand.formatCoverage(ObservationQuality.fullCoverage(17)));
    }

    @Test
    void formatCoveragePartialRoundsToPercent() {
        assertEquals(
                "45%",
                VillageMemoryDebugCommand.formatCoverage(ObservationQuality.withCoverage(9, 20, 10)));
    }
}
