package com.noobk.spmscavenger;

import com.noobk.spmscavenger.CaveOpportunityPolicy.CaveOpportunity;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaveOpportunitySelectionTest {

    private static final BlockPos LEFT = new BlockPos(1, 40, 0);
    private static final BlockPos RIGHT = new BlockPos(2, 41, 0);

    @Test
    void preferenceToScoreInvertsLowerIsBetterKeys() {
        assertEquals(-10, CaveOpportunitySelection.preferenceToScore(10));
        assertTrueOrder(
                CaveOpportunitySelection.preferenceToScore(5),
                CaveOpportunitySelection.preferenceToScore(20));
    }

    @Test
    void committedLandingIsMovedToFront() {
        Map<Long, Integer> keys = CaveOpportunitySelection.preferenceKeyMap();
        keys.put(LEFT.asLong(), 50);
        keys.put(RIGHT.asLong(), 10);
        List<BlockPos> sorted = List.of(RIGHT, LEFT);

        CaveOpportunity held = new CaveOpportunity(RIGHT.asLong(), -10, 0L);
        CaveOpportunitySelection.CommitmentResult result = CaveOpportunitySelection.commitBestScored(
                sorted, keys, held, id -> id == RIGHT.asLong(), 5L);

        assertEquals(RIGHT, result.candidates().get(0));
        assertEquals(RIGHT.asLong(), result.commitment().id());
    }

    @Test
    void marginalChallengerDoesNotReorderList() {
        Map<Long, Integer> keys = CaveOpportunitySelection.preferenceKeyMap();
        keys.put(LEFT.asLong(), 40);
        keys.put(RIGHT.asLong(), 45);
        List<BlockPos> sorted = List.of(LEFT, RIGHT);

        CaveOpportunity held = CaveOpportunityPolicy.arbitrate(null, false, LEFT.asLong(), -40, 0L);
        CaveOpportunitySelection.CommitmentResult result = CaveOpportunitySelection.commitBestScored(
                sorted, keys, held, id -> true, 10L);

        assertEquals(LEFT, result.candidates().get(0));
        assertEquals(LEFT.asLong(), result.commitment().id());
    }

    private static void assertTrueOrder(int better, int worse) {
        if (better <= worse) {
            throw new AssertionError("expected " + better + " > " + worse);
        }
    }
}
