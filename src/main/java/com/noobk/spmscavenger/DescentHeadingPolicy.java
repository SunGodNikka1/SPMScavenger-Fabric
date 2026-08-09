package com.noobk.spmscavenger;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * MI-5H — macro heading selection under descent pressure (D-MIW-035).
 *
 * <p>Scores bounded geometry evidence along each candidate heading without ore clairvoyance.
 * Shared by natural descent expeditions, {@code CONTROLLED_DESCENT}, and later tunnel search.
 */
public final class DescentHeadingPolicy {

    /** Distances along a heading to sample surface/rim geometry. */
    public static final int[] SAMPLE_DISTANCES = {16, 32, 48};

    /** Penalty when a recent expedition already used this sector. */
    public static final int RECENT_HEADING_PENALTY = 35;

    private DescentHeadingPolicy() {
    }

    public record Heading(double x, double z, Direction direction) {

        public int sector(int sectors) {
            return sectorIndex(x, z, sectors);
        }
    }

    public static Heading[] cardinalHeadings() {
        // Eight horizontal headings — MC Direction has only four cardinals.
        return new Heading[] {
                unitHeading(0, -1),
                unitHeading(1, -1),
                unitHeading(1, 0),
                unitHeading(1, 1),
                unitHeading(0, 1),
                unitHeading(-1, 1),
                unitHeading(-1, 0),
                unitHeading(-1, -1)
        };
    }

    private static Heading unitHeading(int dx, int dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        double x = dx / len;
        double z = dz / len;
        Direction direction;
        if (Math.abs(x) >= Math.abs(z)) {
            direction = x >= 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            direction = z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
        return new Heading(x, z, direction);
    }

    /**
     * @param surfaceY heightmap surface at each sample index (same length as distances)
     * @param rimY local rim at each sample; use {@link Integer#MIN_VALUE} when unsampled
     */
    public static int scoreSamples(int mobFeetY, int[] surfaceY, int[] rimY) {
        int score = 0;
        int samples = Math.min(surfaceY.length, rimY.length);
        for (int i = 0; i < samples; i++) {
            score += scoreSample(mobFeetY, surfaceY[i], rimY[i]);
        }
        return score;
    }

    static int scoreSample(int mobFeetY, int surfaceY, int rimY) {
        int score = 0;
        int descentAhead = mobFeetY - surfaceY;
        if (descentAhead >= CaveContextPolicy.MIN_DEPTH_BELOW_SURFACE) {
            score += 25;
            score += Math.min(15, descentAhead);
        }
        if (rimY != Integer.MIN_VALUE) {
            int belowRim = rimY - mobFeetY;
            if (belowRim >= CaveContextPolicy.MIN_DEPTH_BELOW_SURFACE) {
                score += 20;
            }
        }
        if (descentAhead > 0 && descentAhead < CaveContextPolicy.MIN_DEPTH_BELOW_SURFACE) {
            score += descentAhead;
        }
        if (descentAhead < -6) {
            score -= 20;
        }
        return score;
    }

    /**
     * Total heading score including exploration bonuses. Higher wins.
     */
    public static int totalScore(
            int geometryScore, int interestBonus, boolean recentHeading, int randomJitter) {
        int score = geometryScore + interestBonus + randomJitter;
        if (recentHeading) {
            score -= RECENT_HEADING_PENALTY;
        }
        return score;
    }

    public static int sectorIndex(double headingX, double headingZ, int sectors) {
        int count = Math.max(1, sectors);
        double unit = (Math.atan2(headingZ, headingX) + Math.PI * 2.0) % (Math.PI * 2.0);
        return Math.floorMod((int) Math.floor(unit / (Math.PI * 2.0) * count), count);
    }

    public static double normalizeX(double headingX, double headingZ) {
        double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
        if (len < 1.0e-6) {
            return 0.0;
        }
        return headingX / len;
    }

    public static double normalizeZ(double headingX, double headingZ) {
        double len = Math.sqrt(headingX * headingX + headingZ * headingZ);
        if (len < 1.0e-6) {
            return 1.0;
        }
        return headingZ / len;
    }

    public static int projectedBlock(double origin, double heading, int distance) {
        return Mth.floor(origin + heading * distance);
    }

    /** Bounded height evidence for one heading without world coupling in tests. */
    @FunctionalInterface
    public interface TerrainSample {
        /** [0] column surface Y, [1] local rim Y (or {@link Integer#MIN_VALUE}). */
        int[] sample(int blockX, int blockZ);
    }

    /**
     * Picks the best cardinal heading from geometry and exploration bonuses.
     */
    public static Heading chooseBest(
            double mobX,
            double mobZ,
            int mobFeetY,
            TerrainSample terrain,
            int interestBonus,
            java.util.function.IntPredicate recentHeadingSector,
            int headingSectors,
            net.minecraft.util.RandomSource random) {
        Heading bestHeading = cardinalHeadings()[0];
        int bestScore = Integer.MIN_VALUE;
        for (Heading candidate : cardinalHeadings()) {
            int[] surface = new int[SAMPLE_DISTANCES.length];
            int[] rim = new int[SAMPLE_DISTANCES.length];
            for (int i = 0; i < SAMPLE_DISTANCES.length; i++) {
                int x = projectedBlock(mobX, candidate.x(), SAMPLE_DISTANCES[i]);
                int z = projectedBlock(mobZ, candidate.z(), SAMPLE_DISTANCES[i]);
                int[] sample = terrain.sample(x, z);
                surface[i] = sample[0];
                rim[i] = sample.length > 1 ? sample[1] : Integer.MIN_VALUE;
            }
            int geometry = scoreSamples(mobFeetY, surface, rim);
            int score = totalScore(
                    geometry,
                    interestBonus,
                    recentHeadingSector.test(candidate.sector(headingSectors)),
                    random.nextInt(5));
            if (score > bestScore) {
                bestScore = score;
                bestHeading = candidate;
            }
        }
        return bestHeading;
    }
}
