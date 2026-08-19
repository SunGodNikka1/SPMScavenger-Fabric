package com.noobk.spmscavenger;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.Optional;

/**
 * V2-DEF-003b — <b>observation and selection are different data.</b>
 *
 * <pre>
 * OBSERVATION   which eligible resource families existed anywhere in the complete bounded sweep
 * SELECTION     which few candidates deserve expensive path and protection processing
 * </pre>
 *
 * <h2>Why they were merged, and what that cost</h2>
 *
 * The sweep kept only the nearest {@code maxCandidates} positions, and family presence was recorded
 * <b>after</b> that pruning. So:
 *
 * <pre>
 * RAW_IRON required
 * 24 nearer wealth candidates (logs, cobble, coal)
 * one exposed iron ore, farther but still inside the radius
 *   -&gt; iron passes isCandidate
 *   -&gt; buffer already full and iron is farther than #24  -&gt; continue
 *   -&gt; RAW_IRON never recorded
 *   -&gt; the scan claims iron was not found
 * </pre>
 *
 * That is <b>worse than the original stall</b>: it would authorize trade to displace gather on
 * exhaustion evidence that is false. A mob would stop mining iron that was right there.
 *
 * <h2>The separation is structural</h2>
 *
 * {@link #offer} records the family <b>first</b> and unconditionally, then decides whether the
 * position is worth keeping for pass two. The two concerns cannot be reordered by accident because
 * they are no longer two statements a reader has to keep in the right order — recording is not
 * reachable from the pruning branch at all.
 *
 * <p>Pure and free of {@code Level}, so the exact failure above is a unit test rather than a
 * runtime observation: offer 24 near logs and one far iron, then ask what the sweep saw.
 */
public final class GatherScanSweep {

    private final BlockPos[] nearest;
    private final double[] distances;
    private final EnumSet<GatherIntentPolicy.Resource> families =
            EnumSet.noneOf(GatherIntentPolicy.Resource.class);
    private int found;

    public GatherScanSweep(int maxCandidates) {
        this.nearest = new BlockPos[maxCandidates];
        this.distances = new double[maxCandidates];
    }

    /**
     * One eligible pass-one position.
     *
     * <p>Callers must have applied every eligibility rule already — block family, intent, tool,
     * air exposure, tree-base — so that a canopy log the tree rule rejected does not count as log
     * presence.
     *
     * @param family the block's own family, independent of who wanted it
     */
    public void offer(BlockPos pos, double distanceSquared,
            Optional<GatherIntentPolicy.Resource> family) {
        // Observation first, and unconditional. Everything below is selection.
        family.ifPresent(families::add);

        if (found == nearest.length && distanceSquared >= distances[found - 1]) {
            return;
        }
        int at = (found == nearest.length) ? found - 1 : found++;
        while (at > 0 && distances[at - 1] > distanceSquared) {
            distances[at] = distances[at - 1];
            nearest[at] = nearest[at - 1];
            at--;
        }
        distances[at] = distanceSquared;
        nearest[at] = pos.immutable();
    }

    /**
     * Did the complete sweep turn up this family <b>anywhere</b> in radius?
     *
     * <p>Deliberately not "did it select one". A mandatory route's conclusion must not depend on
     * whether an optional candidate happened to be nearer.
     */
    public boolean saw(GatherIntentPolicy.Resource resource) {
        return families.contains(resource);
    }

    public int candidateCount() {
        return found;
    }

    public BlockPos candidate(int index) {
        return nearest[index];
    }

    public double distanceSquared(int index) {
        return distances[index];
    }

    public boolean isEmpty() {
        return found == 0;
    }
}
