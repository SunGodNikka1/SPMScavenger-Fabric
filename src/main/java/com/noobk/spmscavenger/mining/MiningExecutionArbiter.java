package com.noobk.spmscavenger.mining;

/**
 * MI-14C2 — pure permission matrix from {@link ExecutionIntent} to participating goals.
 */
public final class MiningExecutionArbiter {

    private MiningExecutionArbiter() {
    }

    public static ArbitrationDecision decide(ExecutionIntent intent, MiningGoalKind kind) {
        return switch (intent) {
            case NONE, TUNNEL_HANDOFF_PENDING -> ArbitrationDecision.NEUTRAL;
            case CONTROLLED_DESCENT -> switch (kind) {
                case CONTROLLED_DESCENT -> ArbitrationDecision.ALLOW;
                case TUNNEL_SEARCH, GATHER_RESOURCES, SMELT_AT_FURNACE, CRAFT_TORCHES,
                        EXPLORING_ORDINARY, EXPLORING_CAVE_HANDOFF -> ArbitrationDecision.YIELD;
            };
            // TS-M1: tunnelling is the means, gathering is the end. Yielding the end to
            // protect the means would invert the mode - the mob would cut past the ore it just
            // exposed. GATHER_RESOURCES is ALLOW, and because it is not a designated consumer that
            // makes it Cooperative Resource Handoff rather than contention.
            case TUNNEL_SEARCH -> switch (kind) {
                case TUNNEL_SEARCH, GATHER_RESOURCES -> ArbitrationDecision.ALLOW;
                case CONTROLLED_DESCENT, SMELT_AT_FURNACE, CRAFT_TORCHES, EXPLORING_ORDINARY,
                        EXPLORING_CAVE_HANDOFF -> ArbitrationDecision.YIELD;
            };
            case CAVE_HANDOFF -> switch (kind) {
                case EXPLORING_CAVE_HANDOFF -> ArbitrationDecision.ALLOW;
                case CONTROLLED_DESCENT, TUNNEL_SEARCH, GATHER_RESOURCES, SMELT_AT_FURNACE,
                        CRAFT_TORCHES, EXPLORING_ORDINARY -> ArbitrationDecision.YIELD;
            };
        };
    }
}
