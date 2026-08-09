package com.noobk.spmscavenger.mining;

/**
 * MI-14C2 — what mining execution authority the director has assigned from persistent state.
 *
 * <p>Distinct from {@link ExecutionBlocker}, which records why an authorized executor is not
 * receiving {@code MOVE} from the physical scheduler.
 */
public enum ExecutionIntent {
    /** Active {@link MiningProjectMode#CONTROLLED_DESCENT} assignment. */
    CONTROLLED_DESCENT,
    /** An active deliberate horizontal search project. */
    TUNNEL_SEARCH,
    /** Pending {@link MiningProjectEnd#CAVE_FOUND} transition awaiting explore consumption. */
    CAVE_HANDOFF,
    /**
     * Pending {@link MiningProjectEnd#HANDOFF_TUNNEL_SEARCH} with no executor yet — observable
     * state only; arbitration remains {@link ArbitrationDecision#NEUTRAL}.
     */
    TUNNEL_HANDOFF_PENDING,
    /** No actionable mining execution authority. */
    NONE;

    /** May cause ordinary chores to {@link ArbitrationDecision#YIELD}. */
    public boolean isActionable() {
        return this == CONTROLLED_DESCENT || this == CAVE_HANDOFF;
    }
}
