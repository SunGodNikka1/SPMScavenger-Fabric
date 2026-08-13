package com.noobk.spmscavenger.opinion;

import java.util.UUID;

/**
 * Task 43 item 8 — typed evidence for one phase of a yield transaction.
 *
 * <h2>Two phases, because there are two</h2>
 *
 * <pre>
 * YIELD_REQUESTED
 *        ↓
 * YIELD_ENDED  outcome = ACKNOWLEDGED | EXPIRED | STALE_INCUMBENT
 *                      | MANDATORY_INVALIDATION | SUPERSEDED
 * </pre>
 *
 * <p>Deliberately <b>not</b> a separate {@code ACKNOWLEDGED} then {@code COMPLETED} pair.
 * {@code acknowledgeYield} validates, marks the yield, terminalizes the incumbent and finishes the
 * transaction in one call — there is no interval during which it is acknowledged but incomplete.
 * Emitting both would manufacture a transition that does not exist to make the readout look richer,
 * which is the opposite of what this trace is for. If an executor ever acknowledges first and
 * completes later, reality will have gained a phase and the enum can gain one too.
 *
 * <p>Typed rather than an ever-growing detail string: yield now carries enough causal information
 * that packing it into free text would make it unparseable exactly when it matters.
 *
 * @param phase which end of the transaction this is
 * @param outcome how it ended; {@code null} for {@link Phase#REQUESTED}
 * @param originDecisionId the decision that <em>selected the challenger</em> — the cause of the
 *     switch, not the decision that created the incumbent
 */
public record YieldEvent(
        Phase phase,
        long gameTime,
        UUID incumbentIntentId,
        DiscretionaryActivity incumbentActivity,
        DiscretionaryActivity challengerActivity,
        long originDecisionId,
        long requestedAt,
        long expiresAt,
        DiscretionaryDirectorState.YieldOutcome outcome) {

    public enum Phase {
        REQUESTED,
        ENDED
    }

    public static YieldEvent requested(YieldRequest request, long gameTime) {
        return new YieldEvent(
                Phase.REQUESTED,
                gameTime,
                request.incumbentIntentId(),
                request.incumbentActivity(),
                request.challengerActivity(),
                request.originDecisionId(),
                request.requestedAt(),
                request.expiresAt(),
                null);
    }

    public static YieldEvent ended(
            YieldRequest request,
            DiscretionaryDirectorState.YieldOutcome outcome,
            long gameTime) {
        return new YieldEvent(
                Phase.ENDED,
                gameTime,
                request.incumbentIntentId(),
                request.incumbentActivity(),
                request.challengerActivity(),
                request.originDecisionId(),
                request.requestedAt(),
                request.expiresAt(),
                outcome);
    }

    public boolean succeeded() {
        return outcome == DiscretionaryDirectorState.YieldOutcome.ACKNOWLEDGED;
    }

    /** How long the transaction lived, for readouts that want it without recomputing. */
    public long durationTicks() {
        return Math.max(0L, gameTime - requestedAt);
    }
}
