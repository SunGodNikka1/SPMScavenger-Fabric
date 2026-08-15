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
    void mustNotHappen_startCommuteInsideDeadZoneBetweenBoundsAndMinDistance() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        memory.remember(home, 1L, complete(5));
        memory.designateHome(home);
        // ~74 blocks: outside 64² bounds, inside 128-block start cutoff (Bob VR-T1.5a repro).
        BlockPos bob = new BlockPos(74, 64, 0);
        assertFalse(SettlementBoundsPolicy.within(bob, home));
        assertTrue(bob.distSqr(home) > 64L * 64L);
        assertTrue(Math.sqrt(bob.distSqr(home)) < SettlementTuning.COMMUTE_MIN_DISTANCE);
        assertFalse(SettlementReturnPolicy.shouldStartCommute(null, null)); // guards only
        assertTrue(SettlementReturnPolicy.shouldContinueCommuteAt(memory, home, bob));
        assertFalse(SettlementReturnPolicy.shouldStartCommuteAt(memory, home, bob));
    }

    @Test
    void mustHappen_startCommuteBeyondMinDistance() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        memory.remember(home, 1L, complete(5));
        memory.designateHome(home);
        BlockPos far = new BlockPos(200, 64, 0);
        assertTrue(SettlementReturnPolicy.shouldStartCommuteAt(memory, home, far));
    }

    @Test
    void mustNotHappen_continueCommuteOnceInsideBounds() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos home = new BlockPos(0, 64, 0);
        memory.remember(home, 1L, complete(5));
        memory.designateHome(home);
        BlockPos inside = new BlockPos(32, 64, 0);
        assertTrue(SettlementBoundsPolicy.within(inside, home));
        assertFalse(SettlementReturnPolicy.shouldContinueCommuteAt(memory, home, inside));
    }

    @Test
    void mustNotHappen_lowFamiliarityWithoutHomeQualifies() {
        MobVillageMemory memory = new MobVillageMemory();
        BlockPos village = new BlockPos(0, 64, 0);
        memory.remember(village, 1L, complete(5));
        assertFalse(SettlementReturnPolicy.qualifiesForCommute(memory, village));
    }
}
