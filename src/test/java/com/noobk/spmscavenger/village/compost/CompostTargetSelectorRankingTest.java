package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** CLOSE-58-2 — rank before expensive path probes. */
class CompostTargetSelectorRankingTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);
    private static final Vec3 MOB_POS = new Vec3(0.5, 64.0, 0.5);

    @Test
    void close58_2_nearestLateInRawOrderRanksFirstForProbe() {
        List<BlockPos> raw = new ArrayList<>();
        for (int i = 20; i >= 1; i--) {
            raw.add(new BlockPos(i * 4, 64, 0));
        }
        raw.add(new BlockPos(1, 64, 0));

        List<BlockPos> ranked = CompostTargetSelector.rankedProbeOrder(
                raw,
                MOB_POS,
                ANCHOR,
                (pos, anchor) -> true);

        assertFalse(ranked.isEmpty());
        assertEquals(new BlockPos(1, 64, 0), ranked.getFirst(),
                "nearest composter late in raw list must rank ahead of far candidates");
    }

    @Test
    void close58_2_pathProbesNeverExceedBudget() {
        List<BlockPos> raw = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            raw.add(new BlockPos(i, 64, 0));
        }
        List<BlockPos> ranked = CompostTargetSelector.rankedProbeOrder(
                raw, MOB_POS, ANCHOR, (pos, anchor) -> true);
        AtomicInteger probeCount = new AtomicInteger();
        int counted = CompostTargetSelector.countPathProbes(ranked, pos -> {
            probeCount.incrementAndGet();
            return null;
        });
        assertTrue(counted <= CompostTuning.MAX_COMPOSTER_CANDIDATES);
        assertEquals(counted, probeCount.get());
    }

    @Test
    void close58_2_equalDistanceUsesStableBlockPosTieBreak() {
        BlockPos left = new BlockPos(2, 64, 0);
        BlockPos right = new BlockPos(0, 64, 2);
        List<BlockPos> ranked = CompostTargetSelector.rankedProbeOrder(
                List.of(right, left),
                MOB_POS,
                ANCHOR,
                (pos, anchor) -> true);
        assertEquals(2, ranked.size());
        assertEquals(right, ranked.getFirst());
        assertEquals(left, ranked.get(1));
    }

    @Test
    void close58_2_negativeControl_rawOrderAloneWouldProbeFarFirst() {
        BlockPos far = new BlockPos(80, 64, 0);
        BlockPos near = new BlockPos(1, 64, 0);
        List<BlockPos> raw = List.of(far, near);
        assertEquals(far, raw.getFirst(), "raw fact order lists far composter first");
        List<BlockPos> ranked = CompostTargetSelector.rankedProbeOrder(
                raw, MOB_POS, ANCHOR, (pos, anchor) -> true);
        assertEquals(near, ranked.getFirst(),
                "ranking must override raw enumeration order before path probes");
    }
}
