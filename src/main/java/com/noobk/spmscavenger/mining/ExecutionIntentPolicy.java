package com.noobk.spmscavenger.mining;

import java.util.Optional;
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
        // Step 2.5 — active project state outranks the transition that produced it, and the
        // mode decides the intent. Asking "is this a controlled descent" made every other
        // executable mode invisible to the control plane: a running TUNNEL_SEARCH project derived
        // intent NONE, so nothing yielded to it and nothing protected it.
        Optional<ExecutionIntent> active = store.projectOf(mobId)
                .filter(MiningProject::isActive)
                .flatMap(project -> intentOf(project.mode()));
        if (active.isPresent()) {
            return active.get();
        }
        if (store.hasActiveCaveContinuation(mobId, now)) {
            return ExecutionIntent.CAVE_HANDOFF;
        }
        return store.pendingTransition(mobId)
                .map(transition -> fromPending(transition, now))
                .orElse(ExecutionIntent.NONE);
    }

    /** Executable modes only. A catalogued mode with no executor must not claim authority. */
    public static Optional<ExecutionIntent> intentOf(MiningProjectMode mode) {
        return switch (mode) {
            case CONTROLLED_DESCENT -> Optional.of(ExecutionIntent.CONTROLLED_DESCENT);
            case TUNNEL_SEARCH -> Optional.of(ExecutionIntent.TUNNEL_SEARCH);
            default -> Optional.empty();
        };
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
