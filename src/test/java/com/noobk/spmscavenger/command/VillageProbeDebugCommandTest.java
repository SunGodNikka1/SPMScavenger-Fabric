package com.noobk.spmscavenger.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.village.PerceptionCoverage;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class VillageProbeDebugCommandTest {

    @Test
    void formatCoverageFullIsOneHundredPercent() {
        assertEquals("100%", VillageProbeDebugCommand.formatCoverage(new PerceptionCoverage(12, 12)));
    }

    @Test
    void formatCoveragePartialRoundsToPercent() {
        assertEquals("45%", VillageProbeDebugCommand.formatCoverage(new PerceptionCoverage(9, 20)));
    }
}
