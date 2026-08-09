package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.CaveContextPolicy;
import com.noobk.spmscavenger.CaveLandingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * MI-7R R3 — cave opening handoff for controlled descent (reuses MI-6 cave intelligence).
 */
public final class ControlledDescentCaveHandoff {

    /** Stair steps behind the mob whose excavated cells still count as self-created. */
    private static final int SELF_CORRIDOR_BEHIND = 4;

    private static final int AHEAD_PROBE = 2;

    /** Height sampling for subterranean classification (testable without a full {@link Level}). */
    public interface HeightAccess {
        int motionBlockingHeight(int x, int z);

        boolean canSeeSky(BlockPos pos);
    }

    private ControlledDescentCaveHandoff() {
    }

    /**
     * True when breaking into a legitimate subterranean continuation the mob should explore via MI-6
     * instead of continuing to dig.
     */
    public static boolean openedTraversableCave(
            Level level, BlockPos feet, Direction heading, Predicate<BlockPos> standable) {
        return openedTraversableCave(fromLevel(level), feet, heading, standable);
    }

    static boolean openedTraversableCave(
            HeightAccess heights, BlockPos feet, Direction heading, Predicate<BlockPos> standable) {
        return findOpenedCave(heights, feet, heading, standable).isPresent();
    }

    /**
     * MI-14-R2 — evidence-returning replacement for the boolean.
     *
     * <p>The removed early return treated <em>being subterranean</em> as proof of having opened a
     * cave. A staircase is subterranean by construction, so it fired on the mob's own corridor.
     * Cave <b>context</b> ("where am I standing") is not cave <b>opportunity</b> ("is there somewhere
     * legitimate to continue"), and only the second justifies abandoning a descent.
     *
     * <p>A candidate qualifies only when it is standable, at or below the stair position, in genuine
     * subterranean context, and <b>outside the corridor this staircase just cut</b>. That last
     * condition is why the boolean could not be rescued by a larger depth threshold: no threshold
     * distinguishes a mob's own tunnel from a cave, because both are deep.
     */
    public static Optional<CaveOpening> findOpenedCave(
            Level level, BlockPos feet, Direction heading, Predicate<BlockPos> standable) {
        return findOpenedCave(fromLevel(level), feet, heading, standable);
    }

    static Optional<CaveOpening> findOpenedCave(
            HeightAccess heights, BlockPos feet, Direction heading, Predicate<BlockPos> standable) {
        Set<BlockPos> selfCorridor = selfCorridor(feet, heading);
        BlockPos centre = feet.relative(heading, AHEAD_PROBE);
        int mobY = feet.getY();

        for (BlockPos floor : CaveLandingResolver.collectStandable(
                centre.getX(), centre.getZ(), mobY, standable)) {
            if (floor.getY() > mobY) {
                continue;
            }
            if (selfCorridor.contains(floor.immutable())) {
                continue;
            }
            CaveContextPolicy.SpaceKind kind = classifyAt(heights, floor);
            if (kind != CaveContextPolicy.SpaceKind.CAVE
                    && kind != CaveContextPolicy.SpaceKind.RAVINE) {
                continue;
            }
            return Optional.of(new CaveOpening(floor.immutable(), continuationTo(feet, floor), kind));
        }
        return Optional.empty();
    }

    /**
     * Cells this staircase created or is about to create, which therefore cannot be evidence of a
     * discovery.
     *
     * <p>Built from the step geometry rather than from {@code coarseReturnRoute}: the route stores
     * <em>stand</em> positions only, while a step excavates headroom, body and floor
     * ({@link StairStepPlanner#planStep}). Excluding stands alone would leave the corridor's own
     * head and body cells looking like discovered space.
     */
    static Set<BlockPos> selfCorridor(BlockPos feet, Direction heading) {
        Set<BlockPos> cells = new HashSet<>();
        // Behind: the steps just cut. Ahead: the step about to be cut.
        for (int back = 0; back <= SELF_CORRIDOR_BEHIND; back++) {
            BlockPos stand = feet.relative(heading.getOpposite(), back).above(back);
            addColumn(cells, stand);
        }
        StairStepPlan planned = StairStepPlanner.planStep(feet, heading);
        addColumn(cells, planned.nextStandCell());
        for (BlockPos required : planned.requiredBreaks()) {
            cells.add(required.immutable());
        }
        return cells;
    }

    /** A stand cell plus the 2-high space a mob occupies there. */
    private static void addColumn(Set<BlockPos> cells, BlockPos stand) {
        cells.add(stand.immutable());
        cells.add(stand.above().immutable());
    }

    private static Direction continuationTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    static CaveContextPolicy.SpaceKind classifyAt(HeightAccess heights, BlockPos feet) {
        int columnSurface = heights.motionBlockingHeight(feet.getX(), feet.getZ());
        int localRim = sampleLocalRim(heights, feet.getX(), feet.getZ());
        return CaveContextPolicy.classify(new CaveContextPolicy.CaveContextSnapshot(
                feet.getY(), columnSurface, localRim, heights.canSeeSky(feet)));
    }

    static boolean isSubterraneanAt(Level level, BlockPos feet) {
        return isSubterraneanAt(fromLevel(level), feet);
    }

    static boolean isSubterraneanAt(HeightAccess heights, BlockPos feet) {
        int feetY = feet.getY();
        int columnSurface = heights.motionBlockingHeight(feet.getX(), feet.getZ());
        int localRim = sampleLocalRim(heights, feet.getX(), feet.getZ());
        boolean skyVisible = heights.canSeeSky(feet);
        CaveContextPolicy.CaveContextSnapshot snapshot = new CaveContextPolicy.CaveContextSnapshot(
                feetY, columnSurface, localRim, skyVisible);
        return CaveContextPolicy.isSubterranean(snapshot);
    }

    private static HeightAccess fromLevel(Level level) {
        return new HeightAccess() {
            @Override
            public int motionBlockingHeight(int x, int z) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }

            @Override
            public boolean canSeeSky(BlockPos pos) {
                return level.canSeeSky(pos);
            }
        };
    }

    private static int sampleLocalRim(HeightAccess heights, int originX, int originZ) {
        int[] ox = CaveContextPolicy.rimSampleOffsetsX();
        int[] oz = CaveContextPolicy.rimSampleOffsetsZ();
        int[] samples = new int[ox.length];
        for (int i = 0; i < ox.length; i++) {
            samples[i] = heights.motionBlockingHeight(originX + ox[i], originZ + oz[i]);
        }
        return CaveContextPolicy.localRimHeight(samples);
    }
}
