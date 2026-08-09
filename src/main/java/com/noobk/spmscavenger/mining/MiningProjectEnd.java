package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.progression.TaskLifecycle;

/**
 * Why a {@link MiningProject} stopped or was interrupted. Maps to {@link TaskLifecycle} per RFC
 * terminal table.
 */
public enum MiningProjectEnd {
    DEMAND_SATISFIED(TaskLifecycle.SUCCESS),
    TOOL_FAILURE(TaskLifecycle.INTERRUPTED),
    LOW_FOOD(TaskLifecycle.INTERRUPTED),
    INVENTORY_PRESSURE(TaskLifecycle.BLOCKED),
    NO_PROGRESS(TaskLifecycle.RETRY),
    HAZARD(TaskLifecycle.INTERRUPTED),
    SEARCH_BUDGET_EXHAUSTED(TaskLifecycle.SUCCESS),
    COMBAT(TaskLifecycle.INTERRUPTED),
    PLAYER_ORDER(TaskLifecycle.INTERRUPTED),
    /** Handoff to cave continuation without treating descent as failure. */
    CAVE_FOUND(TaskLifecycle.SUCCESS),
    /** Handoff to tunnel search when blocking demand remains in target band. */
    HANDOFF_TUNNEL_SEARCH(TaskLifecycle.SUCCESS);

    private final TaskLifecycle lifecycle;

    MiningProjectEnd(TaskLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public TaskLifecycle lifecycle() {
        return lifecycle;
    }

    public boolean resumable() {
        return lifecycle == TaskLifecycle.INTERRUPTED || lifecycle == TaskLifecycle.RETRY;
    }
}
