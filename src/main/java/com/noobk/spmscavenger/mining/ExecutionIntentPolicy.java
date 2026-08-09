package com.noobk.spmscavenger.mining;

import java.util.UUID;

/**
 * MI-14C2 — derives {@link ExecutionIntent} from persisted mining state only.
 */
public final class ExecutionIntentPolicy {

    /** Must match {@code ExploringGoal} cave-handoff admission window. */
    public static final int CAVE_HANDOFF_LIFETIME_TICKS = 400;

    private ExecutionIntentPolicy() {
    }

    public static ExecutionIntent derive(MiningProjectSavedData store, UUID mobId, long now) {
        if (store.projectOf(mobId).filter(MiningProject::isControlledDescent).isPresent()) {
            return ExecutionIntent.CONTROLLED_DESCENT;
        }
        return store.pendingTransition(mobId)
                .map(transition -> fromPending(transition, now))
                .orElse(ExecutionIntent.NONE);
    }

    private static ExecutionIntent fromPending(MiningTransition transition, long now) {
        if (transition.reason() == MiningProjectEnd.CAVE_FOUND
                && MiningTransition.acceptableCaveHandoff(
                                java.util.Optional.of(transition), now, CAVE_HANDOFF_LIFETIME_TICKS)
                        .isPresent()) {
            return ExecutionIntent.CAVE_HANDOFF;
        }
        if (transition.reason() == MiningProjectEnd.HANDOFF_TUNNEL_SEARCH) {
            return ExecutionIntent.TUNNEL_HANDOFF_PENDING;
        }
        return ExecutionIntent.NONE;
    }
}
