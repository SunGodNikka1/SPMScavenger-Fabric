package com.noobk.spmscavenger.goal;

/** Pure, unit-testable limits for gather-target acquisition and leaf-obstruction recovery. */
final class GatherApproachPolicy {

    private GatherApproachPolicy() {
    }

    static boolean isInitialTreeLog(boolean currentIsLog, boolean belowIsLog) {
        return currentIsLog && !belowIsLog;
    }

    static boolean madeProgress(double previousDistanceSqr, double currentDistanceSqr, double epsilonSqr) {
        return currentDistanceSqr + epsilonSqr < previousDistanceSqr;
    }

    static boolean isApproachCellAllowed(
            boolean collisionEmpty, boolean isLeaf, boolean leafRecoveryEnabled) {
        return collisionEmpty || (leafRecoveryEnabled && isLeaf);
    }

    static boolean mayClearLeaf(
            boolean treeTarget,
            boolean enabled,
            boolean mobGriefing,
            boolean directCellIsLeaf,
            boolean withinTreeRadius,
            boolean buildProtectionAllows,
            int leavesAlreadyCleared,
            int maximumLeaves,
            int stalledTicks,
            int stallThreshold) {
        return treeTarget
                && enabled
                && mobGriefing
                && directCellIsLeaf
                && withinTreeRadius
                && buildProtectionAllows
                && leavesAlreadyCleared < maximumLeaves
                && stalledTicks >= stallThreshold;
    }
}
