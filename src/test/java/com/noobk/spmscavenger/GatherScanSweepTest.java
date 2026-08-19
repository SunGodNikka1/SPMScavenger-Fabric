package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * V2-DEF-003b blocker 2 — <b>observation must survive selection pruning.</b>
 *
 * <p>The previous repair recorded resource families <i>after</i> the nearest-N buffer decided what to
 * keep, so a resource that existed in radius but sat behind N nearer candidates was seen, discarded,
 * and then reported as absent. That is worse than the stall it was fixing: it would authorize trade
 * to displace gather on <b>false exhaustion evidence</b>, sending a mob away from iron that was
 * right there.
 *
 * <p>The earlier tests could not catch it — they asserted that the source contained
 * {@code familyOf(state).ifPresent(lastScanFamilies::add)}, which was true both before and after the
 * pruning line. This class tests the behaviour instead, on a seam with no {@code Level} in it.
 */
class GatherScanSweepTest {

    private static final Optional<GatherIntentPolicy.Resource> LOG =
            Optional.of(GatherIntentPolicy.Resource.LOGS);
    private static final Optional<GatherIntentPolicy.Resource> IRON =
            Optional.of(GatherIntentPolicy.Resource.RAW_IRON);

    /** The exact defect: iron exists, but 24 nearer wealth candidates fill the buffer first. */
    @Test
    void mustHappen_aFamilyBeyondTheBufferIsStillObserved() {
        GatherScanSweep sweep = new GatherScanSweep(24);
        for (int i = 0; i < 24; i++) {
            sweep.offer(new BlockPos(i, 64, 0), i + 1, LOG);
        }
        sweep.offer(new BlockPos(99, 64, 0), 10_000, IRON);

        assertTrue(sweep.saw(GatherIntentPolicy.Resource.RAW_IRON),
                "the sweep met iron in radius; that it lost the nearest-24 race is a SELECTION "
                        + "outcome and must not become an OBSERVATION of absence");
        assertEquals(24, sweep.candidateCount(), "and selection is unchanged - still the nearest 24");
        for (int i = 0; i < 24; i++) {
            assertFalse(sweep.candidate(i).equals(new BlockPos(99, 64, 0)),
                    "the far iron is correctly not worth pass-two processing");
        }
    }

    /** A family nobody offered must never be claimed as seen. */
    @Test
    void mustNotHappen_anUnseenFamilyIsReportedAsObserved() {
        GatherScanSweep sweep = new GatherScanSweep(4);
        sweep.offer(new BlockPos(1, 64, 0), 1, LOG);

        assertTrue(sweep.saw(GatherIntentPolicy.Resource.LOGS));
        assertFalse(sweep.saw(GatherIntentPolicy.Resource.RAW_IRON),
                "false exhaustion evidence is worse than none - it sends the mob away from ore");
        assertFalse(sweep.saw(GatherIntentPolicy.Resource.COAL));
    }

    /** A position with no gatherable family contributes nothing to observation. */
    @Test
    void mustNotHappen_anUnclassifiedBlockIsAttributedToAFamily() {
        GatherScanSweep sweep = new GatherScanSweep(4);
        sweep.offer(new BlockPos(1, 64, 0), 1, Optional.empty());

        for (GatherIntentPolicy.Resource resource : GatherIntentPolicy.Resource.values()) {
            assertFalse(sweep.saw(resource), resource + " was never offered");
        }
        assertEquals(1, sweep.candidateCount(),
                "though it is still a legitimate selection candidate - eligibility was decided "
                        + "before offer() was called");
    }

    /** Selection still keeps the nearest, in order, which pass two depends on. */
    @Test
    void mustHappen_selectionKeepsTheNearestInDistanceOrder() {
        GatherScanSweep sweep = new GatherScanSweep(3);
        sweep.offer(new BlockPos(5, 64, 0), 50, LOG);
        sweep.offer(new BlockPos(1, 64, 0), 10, IRON);
        sweep.offer(new BlockPos(3, 64, 0), 30, LOG);
        sweep.offer(new BlockPos(2, 64, 0), 20, LOG);

        assertEquals(3, sweep.candidateCount());
        assertEquals(new BlockPos(1, 64, 0), sweep.candidate(0));
        assertEquals(new BlockPos(2, 64, 0), sweep.candidate(1));
        assertEquals(new BlockPos(3, 64, 0), sweep.candidate(2));
        assertTrue(sweep.saw(GatherIntentPolicy.Resource.LOGS),
                "including the log at distance 50 that was pushed out of the buffer");
    }

    /** An empty sweep observes nothing and selects nothing. */
    @Test
    void mustHappen_anEmptySweepIsEmptyInBothSenses() {
        GatherScanSweep sweep = new GatherScanSweep(8);

        assertTrue(sweep.isEmpty());
        assertEquals(0, sweep.candidateCount());
        for (GatherIntentPolicy.Resource resource : GatherIntentPolicy.Resource.values()) {
            assertFalse(sweep.saw(resource));
        }
    }
}
