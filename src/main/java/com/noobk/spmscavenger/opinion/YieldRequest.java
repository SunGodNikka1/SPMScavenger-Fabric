package com.noobk.spmscavenger.opinion;

import java.util.Objects;
import java.util.UUID;

/**
 * D-GAO-051 — one generic voluntary-yield contract, replacing per-pair booleans.
 *
 * <h2>What it replaces</h2>
 *
 * {@code restYieldRequested} / {@code exploreYieldRequested} were two flags describing one concept,
 * and the pair grows quadratically: a third activity needs six. Worse, a bare boolean carries no
 * identity, so a request raised against one execution could be observed by whatever happened to be
 * running when the executor next looked.
 *
 * <h2>What it is not</h2>
 *
 * <b>Not movement authority.</b> It communicates a director decision to the owning executor; the
 * executor remains responsible for reaching a safe finite yield point and for reporting lifecycle
 * truth. A director that could stop an executor mid-swing would be arbitration by remote control,
 * which is exactly the coupling the control plane exists to remove.
 *
 * <p><b>Not for mandatory authority.</b> Survival, combat, player command, shelter hold and
 * mandatory work do not negotiate — they invalidate. Routing them through a voluntary contract would
 * make them refusable.
 *
 * @param incumbentIntentId the execution being asked to yield; a request cannot outlive it
 * @param incumbentActivity what is running
 * @param challengerKey the exact candidate that asked for the slot
 * @param originDecisionId the decision that raised it, for trace correlation
 * @param requestedAt game time raised
 * @param expiresAt bounded lifecycle — an unanswered request must not linger
 */
public record YieldRequest(
        UUID incumbentIntentId,
        DiscretionaryActivity incumbentActivity,
        DiscretionaryCandidateKey challengerKey,
        long originDecisionId,
        long requestedAt,
        long expiresAt) {

    /** How long an unanswered request stands before it is treated as declined. */
    public static final int LIFETIME_TICKS = 200;

    public YieldRequest {
        Objects.requireNonNull(incumbentIntentId, "incumbentIntentId");
        Objects.requireNonNull(incumbentActivity, "incumbentActivity");
        Objects.requireNonNull(challengerKey, "challengerKey");
    }

    public static YieldRequest of(
            DiscretionaryIntent incumbent,
            DiscretionaryCandidateKey challenger,
            long originDecisionId,
            long now) {
        return new YieldRequest(
                incumbent.intentId(),
                incumbent.activity(),
                challenger,
                originDecisionId,
                now,
                now + LIFETIME_TICKS);
    }

    /** Existing singleton-activity seam; SOCIAL must use an exact subject-bearing key. */
    public static YieldRequest of(
            DiscretionaryIntent incumbent,
            DiscretionaryActivity challenger,
            long originDecisionId,
            long now) {
        return of(
                incumbent,
                DiscretionaryCandidateKey.singleton(challenger),
                originDecisionId,
                now);
    }

    /**
     * Whether this request applies to the execution asking about it.
     *
     * <p>Identity-bound on purpose. A request raised against intent A must not be answered by
     * intent B, even for the same activity at the same position — that is how a stale request
     * yields a freshly issued execution the director never intended to interrupt.
     */
    public boolean appliesTo(DiscretionaryIntent incumbent, long now) {
        return incumbent != null
                && incumbent.intentId().equals(incumbentIntentId)
                && !expired(now);
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    public boolean isFor(DiscretionaryActivity activity) {
        return incumbentActivity == activity;
    }

    /** Compatibility/readout view; arbitration must compare {@link #challengerKey()}. */
    public DiscretionaryActivity challengerActivity() {
        return challengerKey.activity();
    }
}
