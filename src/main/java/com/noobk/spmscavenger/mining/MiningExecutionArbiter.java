package com.noobk.spmscavenger.mining;

/**
 * MI-14C2 — pure permission matrix from {@link ExecutionIntent} to participating goals.
 */
public final class MiningExecutionArbiter {

    private MiningExecutionArbiter() {
    }

    public static ArbitrationDecision decide(ExecutionIntent intent, MiningGoalKind kind) {
        return switch (intent) {
            // TUNNEL_SEARCH is NEUTRAL until step 3 gives it a designated consumer and a real
            // row. Neutral is the honest placeholder: it grants no authority to a mode whose
            // executor does not exist yet.
            case NONE, TUNNEL_HANDOFF_PENDING, TUNNEL_SEARCH -> ArbitrationDecision.NEUTRAL;
            case CONTROLLED_DESCENT -> switch (kind) {
                case CONTROLLED_DESCENT -> ArbitrationDecision.ALLOW;
                case GATHER_RESOURCES, SMELT_AT_FURNACE, CRAFT_TORCHES, EXPLORING_ORDINARY,
                        EXPLORING_CAVE_HANDOFF -> ArbitrationDecision.YIELD;
            };
            case CAVE_HANDOFF -> switch (kind) {
                case EXPLORING_CAVE_HANDOFF -> ArbitrationDecision.ALLOW;
                case CONTROLLED_DESCENT, GATHER_RESOURCES, SMELT_AT_FURNACE, CRAFT_TORCHES,
                        EXPLORING_ORDINARY -> ArbitrationDecision.YIELD;
            };
        };
    }
}
