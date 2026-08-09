package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescentHeadingPolicyTest {

    @Test
    void choosesHeadingWithDeeperDepressionAhead() {
        int mobY = 70;
        DescentHeadingPolicy.Heading chosen = DescentHeadingPolicy.chooseBest(
                0.0,
                0.0,
                mobY,
                (x, z) -> {
                    if (x > 0) {
                        return new int[] {58, 72};
                    }
                    return new int[] {70, 72};
                },
                0,
                sector -> false,
                12,
                net.minecraft.util.RandomSource.create(1L));
        assertEquals(Direction.EAST, chosen.direction());
    }

    @Test
    void scoreSampleRewardsMeaningfulDepression() {
        int shallow = DescentHeadingPolicy.scoreSample(70, 69, 72);
        int deep = DescentHeadingPolicy.scoreSample(70, 55, 72);
        assertTrue(deep > shallow);
    }

    @Test
    void cardinalHeadingsCoverEightDirections() {
        assertEquals(8, DescentHeadingPolicy.cardinalHeadings().length);
    }
}
