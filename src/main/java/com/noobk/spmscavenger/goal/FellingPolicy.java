package com.noobk.spmscavenger.goal;

/**
 * Pure policy for the lifetime and size of one approved tree-felling session.
 *
 * <p>The distinction between a hard interruption and a soft acquisition boundary is deliberate.
 * Combat, a disabled feature, {@code mobGriefing=false}, or a lost target must stop immediately.
 * Having enough material to begin a crafting step only means "do not start another tree"; it must
 * not strand the already-approved trunk after its first block.</p>
 */
final class FellingPolicy {

    private FellingPolicy() {
    }

    static boolean mayContinueGoal(
            boolean hardConditionsPass,
            boolean approvedTreeInProgress,
            boolean acquisitionStillNeeded) {
        return hardConditionsPass && (approvedTreeInProgress || acquisitionStillNeeded);
    }

    static boolean mayTakeNextLog(
            boolean harvestedBlockWasLog,
            boolean nextBlockIsLog,
            int felledLogs,
            int maximumLogs) {
        return harvestedBlockWasLog
                && nextBlockIsLog
                && felledLogs >= 1
                && felledLogs < maximumLogs;
    }
}
