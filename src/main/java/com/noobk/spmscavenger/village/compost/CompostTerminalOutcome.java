package com.noobk.spmscavenger.village.compost;

/** Terminal compost episode outcomes (task-58). */
public enum CompostTerminalOutcome {
    COMMITTED,
    COMMITTED_NO_LEVEL_CHANGE,
    ABORTED;

    public static CompostTerminalOutcome fromCommitResult(CompostTransaction.CommitResult result) {
        if (result == null || result.outcome() != CompostTransaction.CommitOutcome.COMMITTED) {
            return ABORTED;
        }
        return result.levelAfter() > result.levelBefore()
                ? COMMITTED
                : COMMITTED_NO_LEVEL_CHANGE;
    }
}
