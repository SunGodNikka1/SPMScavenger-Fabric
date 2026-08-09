package com.noobk.spmscavenger.mining;

import java.util.UUID;

/**
 * MI-14C2 — derives {@link ExecutionIntent} from persisted mining state only.
 */
public final class ExecutionIntentPolicy {

    /**
     * Sole definition of the cave-handoff admission window.
     *
     * <p>L1 of the Lifetime Semantics Sweep: {@code ExploringGoal} held a private copy of this
     * number, kept in step by a comment saying it must match. A one-line edit to either would have
     * desynchronised admission from intent and goal classification - the same CAVE_HANDOFF vs
     * EXPLORING_ORDINARY divergence MI-14C2-M2 repaired, arriving by a different route.
     */
    public static final int CAVE_HANDOFF_LIFETIME_TICKS = 400;

    private ExecutionIntentPolicy() {
    }

    public static ExecutionIntent derive(MiningProjectSavedData store, UUID mobId, long now) {
        store.pruneExpiredCommitments(mobId, now);
        if (store.projectOf(mobId).filter(MiningProject::isControlledDescent).isPresent()) {
            return ExecutionIntent.CONTROLLED_DESCENT;
        }
        if (store.hasActiveCaveContinuation(mobId, now)) {
            return ExecutionIntent.CAVE_HANDOFF;
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
