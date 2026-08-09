package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveLandingResolverTest {

    @Test
    void dyProbeOrderStartsAtMobBandThenDown() {
        int[] order = CaveLandingResolver.dyProbeOrder();
        assertEquals(0, order[0]);
        assertEquals(-1, order[1]);
        assertEquals(1, order[2]);
    }

    @Test
    void collectStandableRespectsCapAndPredicate() {
        Set<BlockPos> allowed = new HashSet<>();
        allowed.add(new BlockPos(0, 32, 0));
        allowed.add(new BlockPos(1, 31, 0));
        allowed.add(new BlockPos(2, 30, 0));
        List<BlockPos> found = CaveLandingResolver.collectStandable(
                0, 0, 32, allowed::contains);
        assertFalse(found.isEmpty());
        assertTrue(found.size() <= CaveLandingResolver.MAX_CANDIDATES);
        assertTrue(found.contains(new BlockPos(0, 32, 0)));
    }

    @Test
    void emptyWhenNothingStandable() {
        assertTrue(CaveLandingResolver.collectStandable(0, 0, 64, pos -> false).isEmpty());
    }
}
