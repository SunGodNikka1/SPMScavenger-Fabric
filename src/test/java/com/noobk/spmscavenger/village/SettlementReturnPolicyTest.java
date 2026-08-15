package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** V1.5-C — commute policy unit tests. */
class SettlementReturnPolicyTest {

    private static ObservationQuality complete(int admitted) {
        return ObservationQuality.fullCoverage(admitted);
    }

    @Test
    void mustHappen_homeQualifiesForCommuteTarget() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        memory.remember(home, 1L, complete(5));
        memory.designateHome(home);
        assertTrue(SettlementReturnPolicy.commuteTarget(memory, new BlockPos(200, 64, 200))
                .map(home::equals)
                .orElse(false));
    }

    @Test
    void mustHappen_highFamiliarityVillageQualifiesWhenNoHome() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos village = new BlockPos(0, 64, 0);
        memory.remember(village, 1L, complete(5));
        memory.putRelationship(village, new SettlementRelationship(700, 1L, 1));
        assertTrue(SettlementReturnPolicy.qualifiesForCommute(memory, village));
    }

    @Test
    void mustNotHappen_lowFamiliarityWithoutHomeQualifies() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos village = new BlockPos(0, 64, 0);
        memory.remember(village, 1L, complete(5));
        assertFalse(SettlementReturnPolicy.qualifiesForCommute(memory, village));
    }
}
