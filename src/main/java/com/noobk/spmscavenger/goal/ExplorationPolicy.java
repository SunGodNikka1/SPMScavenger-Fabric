package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;

/** Pure geometry and state-transition policy for {@link ExploringGoal}. */
final class ExplorationPolicy {

    enum FailureAction {
        RETRY_WAYPOINT,
        SKIP_WAYPOINT,
        DROP_REJOIN,
        ABANDON_PATH,
        ABANDON_SIMULATION_FRONTIER
    }

    enum ResumeAction {
        KEEP_CURRENT,
        SKIP_CURRENT,
        REJOIN_HEADING
    }

    private ExplorationPolicy() {
    }

    /** Only a confirmed absence of a persistent stay-near order permits a long expedition. */
    static boolean allowsExpedition(PlayerMobs.StayAnchorState state) {
        return state == PlayerMobs.StayAnchorState.ABSENT;
    }

    /**
     * A navigation path ending is not itself proof of failure: target/path bookkeeping can settle
     * before the entity enters the accepted arrival radius. Preserve a short grace window while
     * retaining the independent no-progress timeout.
     */
    static boolean navigationFailed(
            boolean navigationDone,
            long navigationDoneTicks,
            long noProgressTicks,
            long navigationDoneGraceTicks,
            long stallTicks) {
        return noProgressTicks >= stallTicks
                || (navigationDone && navigationDoneTicks >= navigationDoneGraceTicks);
    }

    static double forwardProgress(
            double originX, double originZ, double headingX, double headingZ,
            double x, double z) {
        return (x - originX) * headingX + (z - originZ) * headingZ;
    }

    static double lateralDistance(
            double originX, double originZ, double headingX, double headingZ,
            double x, double z) {
        double dx = x - originX;
        double dz = z - originZ;
        double lateral = dx * -headingZ + dz * headingX;
        return Math.abs(lateral);
    }

    static double projectedX(
            double originX, double headingX, double headingZ, double forward, double lateral) {
        return originX + headingX * forward - headingZ * lateral;
    }

    static double projectedZ(
            double originZ, double headingX, double headingZ, double forward, double lateral) {
        return originZ + headingZ * forward + headingX * lateral;
    }

    static ResumeAction resumeAction(
            double mobForward,
            double waypointForward,
            double mobLateral,
            double forwardSkipMargin,
            double lateralRejoinThreshold) {
        if (mobForward > waypointForward + forwardSkipMargin) {
            return ResumeAction.SKIP_CURRENT;
        }
        if (mobLateral > lateralRejoinThreshold) {
            return ResumeAction.REJOIN_HEADING;
        }
        return ResumeAction.KEEP_CURRENT;
    }

    /**
     * Longest path a single {@code createPath} request may sensibly ask for.
     *
     * <p>Vanilla A* refuses to expand any node at or beyond {@code FOLLOW_RANGE} blocks from the
     * start, so a longer request is not a slow path but an impossible one: it returns a partial
     * path whose {@code canReach()} is false. Half the follow range leaves room for the detours a
     * real route takes around terrain, since that cutoff measures straight-line distance from the
     * start rather than distance travelled.
     */
    static double maxPathStep(double followRange) {
        return Math.max(8.0, Math.min(24.0, followRange * 0.5));
    }

    /**
     * One coordinate of a point on the line from origin to target, no further than {@code maxStep}
     * from the origin. Returns the target itself when it is already within reach.
     */
    static double stepCoordinate(double origin, double target, double distance, double maxStep) {
        if (distance <= maxStep || distance <= 1.0e-4) {
            return target;
        }
        return origin + (target - origin) * (maxStep / distance);
    }

    /**
     * Whether two mobs travel together, given how each feels about the other.
     *
     * <p>Deliberately mutual and deliberately strict about neutral. SPM starts every pair at
     * exactly neutral, so accepting neutral would make every mob in earshot a travelling companion
     * and the choice would mean nothing. Requiring both sides above neutral makes a shared journey
     * the consequence of an actual history - in an ordinary world, of having greeted each other.
     */
    static boolean travelsTogether(Float selfFeeling, Float otherFeeling, float neutral) {
        return selfFeeling != null && otherFeeling != null
                && selfFeeling > neutral && otherFeeling > neutral;
    }

    /** Side-by-side rather than single file: companions alternate sides of the leader's line. */
    static double companionLateralOffset(int slot) {
        int step = slot / 2 + 1;
        return (slot % 2 == 0 ? 1.0 : -1.0) * Math.min(6.0, 2.0 * step);
    }

    static boolean meaningfulLocalTrip(double distanceSqr, double minimumDistance) {
        return distanceSqr >= minimumDistance * minimumDistance;
    }

    static boolean madeProgress(double previousDistanceSqr, double currentDistanceSqr, double epsilonSqr) {
        return currentDistanceSqr + epsilonSqr < previousDistanceSqr;
    }

    static FailureAction failureAction(
            boolean simulationFrontier,
            int waypointFailures,
            int expeditionFailures,
            int maximumWaypointFailures,
            int maximumExpeditionFailures,
            boolean rejoining,
            boolean hasAnotherWaypoint) {
        if (simulationFrontier) {
            return FailureAction.ABANDON_SIMULATION_FRONTIER;
        }
        if (expeditionFailures >= maximumExpeditionFailures) {
            return FailureAction.ABANDON_PATH;
        }
        if (waypointFailures < maximumWaypointFailures) {
            return FailureAction.RETRY_WAYPOINT;
        }
        if (rejoining) {
            return FailureAction.DROP_REJOIN;
        }
        return hasAnotherWaypoint ? FailureAction.SKIP_WAYPOINT : FailureAction.ABANDON_PATH;
    }

    static long regionKey(int blockX, int blockZ, int regionSizeChunks) {
        int size = Math.max(1, regionSizeChunks);
        int regionX = Math.floorDiv(blockX >> 4, size);
        int regionZ = Math.floorDiv(blockZ >> 4, size);
        return ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
    }

    static int headingSector(double headingX, double headingZ, int sectors) {
        int count = Math.max(1, sectors);
        double unit = (Math.atan2(headingZ, headingX) + Math.PI * 2.0) % (Math.PI * 2.0);
        return Math.floorMod((int) Math.floor(unit / (Math.PI * 2.0) * count), count);
    }
}
