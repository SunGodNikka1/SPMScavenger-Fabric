package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.AttachmentBand;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.SettlementTuning;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class VillageSettlementStatusCommandTest {

    @Test
    void mustHappen_formatReportMatchesVrT15bFixture() {
        BlockPos anchor = new BlockPos(-11666, 82, 7709);
        SettlementRelationship relationship = new SettlementRelationship(27, 184_220L, 0);

        String report = VillageSettlementStatusCommand.formatReport(
                "Alice",
                anchor,
                relationship,
                false,
                true);

        assertTrue(report.contains("Alice settlement status"));
        assertTrue(report.contains("Nearest: -11666, 82, 7709"));
        assertTrue(report.contains("Familiarity: 27 / " + SettlementTuning.MAX_FAMILIARITY));
        assertTrue(report.contains("Band: " + AttachmentBand.LOW.name()));
        assertTrue(report.contains("Social events: 0"));
        assertTrue(report.contains("Home: false"));
        assertTrue(report.contains("Last visit: 184220"));
        assertTrue(report.contains("Inside bounds: true"));
    }
}
