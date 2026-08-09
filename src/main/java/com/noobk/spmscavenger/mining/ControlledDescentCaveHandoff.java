package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.CaveContextPolicy;
import com.noobk.spmscavenger.CaveLandingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Predicate;

/**
 * MI-7R R3 — cave opening handoff for controlled descent (reuses MI-6 cave intelligence).
 */
public final class ControlledDescentCaveHandoff {

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
        if (isSubterraneanAt(heights, feet)) {
            return true;
        }
        BlockPos centre = feet.relative(heading, AHEAD_PROBE);
        int mobY = feet.getY();
        for (BlockPos floor : CaveLandingResolver.collectStandable(
                centre.getX(), centre.getZ(), mobY, standable)) {
            if (floor.getY() > mobY) {
                continue;
            }
            if (isSubterraneanAt(heights, floor)) {
                return true;
            }
        }
        return false;
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
