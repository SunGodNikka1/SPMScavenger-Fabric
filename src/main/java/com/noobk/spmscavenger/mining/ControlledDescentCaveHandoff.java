package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.CaveContextPolicy;
import com.noobk.spmscavenger.CaveLandingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Deque;
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

    /** Bounded connected-air search from the excavation boundary. */
    private static final int MAX_BREAKTHROUGH_CELLS = 64;
    private static final int BREAKTHROUGH_RADIUS = 6;

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
            Level level, StairStepPlan completedStep, Direction heading,
            Predicate<BlockPos> passable, Predicate<BlockPos> standable) {
        return findOpenedCave(
                fromLevel(level), completedStep,
                selfCorridor(completedStep.nextStandCell(), heading), passable, standable);
    }

    /**
     * Legacy boolean path. Reconstructs the step that <em>ended</em> at {@code feet}, because
     * callers of the boolean have no plan object; production must pass its real
     * {@link StairStepPlan} instead.
     */
    static Optional<CaveOpening> findOpenedCave(
            HeightAccess heights, BlockPos feet, Direction heading, Predicate<BlockPos> standable) {
        BlockPos previousStand = feet.relative(heading.getOpposite()).above();
        return findOpenedCave(
                heights, StairStepPlanner.planStep(previousStand, heading),
                selfCorridor(feet, heading), standable, standable);
    }

    /**
     * MI-14-R2b — connected breakthrough evidence.
     *
     * <p>R2a stopped the mob calling its own staircase a cave, but it still proved only that a
     * standable cave-like floor existed <em>nearby</em>. Nearby is not opened: the probe read world
     * state through unbroken stone, so a cave two blocks ahead behind an intact wall satisfied every
     * check — standable, subterranean, outside the corridor — and fired {@code CAVE_FOUND} at a
     * place the mob could not enter.
     *
     * <p>The invariant is topological, not geometric: a candidate must be <b>reachable through air
     * from the cells this step just excavated</b>. The flood starts at the excavation boundary, so
     * an intact wall is simply impassable and the volume behind it is never visited.
     *
     * <p>This also sidesteps the {@link CaveLandingResolver} budget problem rather than inheriting
     * it. A breakthrough is adjacent to what was just broken by definition, so a small bounded flood
     * is both cheaper and more accurate than a 180-probe volume scan whose outer radii are
     * unreachable anyway.
     *
     * <p><b>MI-14-R2c:</b> {@code completedStep} is the step that was <em>just excavated</em>, never
     * the one about to be. Reconstructing it from the mob's current feet plans the <em>next</em>
     * step, whose cells are still solid rock — which both hides the real breakthrough and lets a
     * cave touching the unbroken wall ahead qualify.
     *
     * @param passable cells a mob could move through — no collision, no fluid
     * @param standable cells a mob could stand in — passable, with a sturdy floor
     */
    static Optional<CaveOpening> findOpenedCave(
            HeightAccess heights,
            StairStepPlan completedStep,
            Set<BlockPos> selfCorridor,
            Predicate<BlockPos> passable,
            Predicate<BlockPos> standable) {

        BlockPos origin = completedStep.standCell();
        // MI-14-R2e: connectivity is measured in mob-sized space. A one-block-high slit connects
        // air but not a two-block-tall PlayerMob, and a handoff it cannot walk through is a handoff
        // it will fail. Standability still decides where the flood may stop.
        Predicate<BlockPos> occupiable =
                cell -> passable.test(cell) && passable.test(cell.above());
        Deque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();

        // Seeds: air immediately beyond the cells this step opened. Anything still solid is not a
        // seed, which is exactly why a hidden cave behind a wall cannot be reached.
        for (BlockPos excavated : excavatedCells(completedStep)) {
            // MI-14-R2c — a cell only counts as excavated if it is open *now*. Belt-and-braces
            // against a caller handing us a step that has not been dug yet: a solid wall seeds
            // nothing, so the through-wall bug cannot reappear one step ahead.
            if (!passable.test(excavated)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                BlockPos neighbour = excavated.relative(face).immutable();
                if (selfCorridor.contains(neighbour) || !seen.add(neighbour)) {
                    continue;
                }
                if (occupiable.test(neighbour)) {
                    frontier.add(neighbour);
                }
            }
        }

        int visited = 0;
        while (!frontier.isEmpty() && visited < MAX_BREAKTHROUGH_CELLS) {
            BlockPos cell = frontier.poll();
            visited++;

            if (standable.test(cell)) {
                CaveContextPolicy.SpaceKind kind = classifyAt(heights, cell);
                if (kind == CaveContextPolicy.SpaceKind.CAVE
                        || kind == CaveContextPolicy.SpaceKind.RAVINE) {
                    // Measured from the breakthrough, not from the mob's feet. "Which way does the
                    // cave lie from the hole I just made" is the question continuation answers; from
                    // the stand cell a diagonal landing ties and resolves along the staircase axis,
                    // which points back down the tunnel rather than into the discovery.
                    BlockPos from = nearestExcavated(completedStep, cell);
                    return Optional.of(new CaveOpening(cell, continuationTo(from, cell), kind));
                }
            }

            for (Direction face : Direction.values()) {
                BlockPos next = cell.relative(face).immutable();
                if (selfCorridor.contains(next) || !seen.add(next)) {
                    continue;
                }
                if (withinReach(origin, next) && occupiable.test(next)) {
                    frontier.add(next);
                }
            }
        }
        return Optional.empty();
    }

    /** Cells this step physically opened — the only legitimate seeds for a breakthrough. */
    private static Set<BlockPos> excavatedCells(StairStepPlan step) {
        Set<BlockPos> cells = new HashSet<>(step.requiredBreaks());
        cells.add(step.nextStandCell());
        return cells;
    }

    /** The opened cell closest to the discovery — the point the breakthrough happened at. */
    private static BlockPos nearestExcavated(StairStepPlan step, BlockPos landing) {
        BlockPos best = step.nextStandCell();
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : excavatedCells(step)) {
            double distance = cell.distSqr(landing);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        return best;
    }

    private static boolean withinReach(BlockPos origin, BlockPos cell) {
        return Math.abs(cell.getX() - origin.getX()) <= BREAKTHROUGH_RADIUS
                && Math.abs(cell.getZ() - origin.getZ()) <= BREAKTHROUGH_RADIUS
                && Math.abs(cell.getY() - origin.getY()) <= BREAKTHROUGH_RADIUS;
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
        // MI-14-R2d: excavation history only. Planned cells were once included to stop an unbroken
        // wall ahead being read as evidence, but the R2c seed guard already does that - solid cells
        // cannot seed. Keeping them here caused the opposite failure: a cave that is *already open*
        // directly ahead was masked, because its air occupied cells the planner would have dug.
        // Self-created means dug, not intended.
        for (int back = 0; back <= SELF_CORRIDOR_BEHIND; back++) {
            BlockPos stand = feet.relative(heading.getOpposite(), back).above(back);
            addColumn(cells, stand);
        }
        return cells;
    }

    /**
     * Every cell a stair step cut at this stand position.
     *
     * <p>MI-14-R2c: three high, not two. {@link StairStepPlanner#planStep} breaks
     * {@code forwardFeet.above()}, {@code forwardFeet} and {@code nextStand} — which, measured from
     * the resulting stand cell, is {@code stand}, {@code stand+1} and {@code stand+2}. A 2-high
     * column left each step's own headroom outside the corridor, so the flood could find the
     * staircase's dug ceiling and report it as a cave.
     */
    private static void addColumn(Set<BlockPos> cells, BlockPos stand) {
        cells.add(stand.immutable());
        cells.add(stand.above().immutable());
        cells.add(stand.above(2).immutable());
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
