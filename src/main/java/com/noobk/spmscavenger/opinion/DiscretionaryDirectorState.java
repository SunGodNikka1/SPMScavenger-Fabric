package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;

import java.util.Objects;
import java.util.Optional;

/**
 * GAO-4 — per-mob discretionary director state and tick logic.
 */
public final class DiscretionaryDirectorState {

    private DiscretionaryIntent intent;
    private DiscretionaryActivity incumbentActivity;
    private float incumbentUtility;
    private boolean restYieldRequested;
    private boolean exploreYieldRequested;
    private final OpinionDecisionTrace trace = new OpinionDecisionTrace();

    public Optional<DiscretionaryIntent> intent() {
        return Optional.ofNullable(intent);
    }

    public OpinionDecisionTrace trace() {
        return trace;
    }

    public boolean restYieldRequested() {
        return restYieldRequested;
    }

    public boolean exploreYieldRequested() {
        return exploreYieldRequested;
    }

    public Optional<DiscretionaryActivity> incumbentActivity() {
        return Optional.ofNullable(incumbentActivity);
    }

    public void tick(DirectorTickInput input) {
        Objects.requireNonNull(input, "input");
        restYieldRequested = false;
        exploreYieldRequested = false;

        if (!input.opinionEnabled()) {
            invalidateActive(IntentLifecycle.INVALIDATED, InvalidationCause.OPINION_DISABLED, input.gameTime());
            return;
        }
        if (input.frozen()) {
            invalidateActive(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, input.gameTime());
            return;
        }

        InvalidationCause mandatory = DiscretionaryEligibility.invalidationForObservation(
                input.observation(), input.combatTarget());
        if (mandatory != InvalidationCause.NONE) {
            invalidateActive(IntentLifecycle.INVALIDATED, mandatory, input.gameTime());
            return;
        }

        expirePendingIfNeeded(input.gameTime());

        Optional<ScoringResult> scoring = IdleOpportunityPolicy.score(input.scoringInput());
        if (scoring.isEmpty()) {
            return;
        }

        recordScores(scoring.get(), input.gameTime());
        ActivityUtilityBreakdown top = scoring.get().top().orElseThrow();
        float runnerUp = runnerUpUtility(scoring.get());

        if (top.total() < DiscretionaryDirectorConstants.ACTIVATION_THRESHOLD) {
            trace.record(
                    input.gameTime(),
                    OpinionDecisionTrace.Stage.ABSTAIN,
                    intent == null ? null : intent.intentId(),
                    top.activity(),
                    "top=" + top.total());
            clearNonRunningIntent(IntentLifecycle.ABSTAINED, InvalidationCause.NONE, input.gameTime());
            return;
        }

        trace.record(
                input.gameTime(),
                OpinionDecisionTrace.Stage.SELECT,
                intent == null ? null : intent.intentId(),
                top.activity(),
                "top=" + top.total() + " runnerUp=" + runnerUp);

        DiscretionaryActivity winner = top.activity();
        if (!canSwitchTo(winner, top.total(), input.gameTime())) {
            updateYieldRequests(winner, top.total(), input.gameTime());
            return;
        }

        if (intent == null
                || !intent.isActive()
                || intent.activity() != winner
                || intent.lifecycle() == IntentLifecycle.PENDING && intent.isExpiredPending(input.gameTime())) {
            issuePending(winner, top.total(), runnerUp, input.gameTime());
        }

        updateYieldRequests(winner, top.total(), input.gameTime());
    }

    public boolean hasActionableIntent(DiscretionaryActivity activity) {
        return intent != null
                && intent.isActive()
                && intent.activity() == activity;
    }

    public void adopt(DiscretionaryActivity activity, long gameTime) {
        if (intent == null || intent.activity() != activity || !intent.isActive()) {
            return;
        }
        intent.markAdopted(gameTime);
        incumbentActivity = activity;
        incumbentUtility = intent.selectedUtility();
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.ADOPT,
                intent.intentId(),
                activity,
                "utility=" + intent.selectedUtility());
    }

    public void markRunning(DiscretionaryActivity activity, long gameTime) {
        if (intent == null || intent.activity() != activity) {
            return;
        }
        intent.markRunning();
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.EXECUTOR,
                intent.intentId(),
                activity,
                "running");
    }

    public void markYield(DiscretionaryActivity released, DiscretionaryActivity adopted, long gameTime) {
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.YIELD,
                intent == null ? null : intent.intentId(),
                adopted,
                "released=" + released);
    }

    public void markTerminal(
            IntentLifecycle terminal, InvalidationCause cause, long gameTime, String detail) {
        if (intent == null || intent.lifecycle().isTerminal()) {
            return;
        }
        intent.markTerminal(terminal, cause);
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.TERMINAL,
                intent.intentId(),
                intent.activity(),
                terminal + (detail == null || detail.isBlank() ? "" : ":" + detail));
        if (incumbentActivity == intent.activity()) {
            incumbentActivity = null;
            incumbentUtility = 0f;
        }
        intent = null;
    }

    private void issuePending(
            DiscretionaryActivity winner, float utility, float runnerUp, long gameTime) {
        if (intent != null && intent.isActive()) {
            intent.markTerminal(IntentLifecycle.INTERRUPTED, InvalidationCause.SUPERSEDED);
            trace.record(
                    gameTime,
                    OpinionDecisionTrace.Stage.TERMINAL,
                    intent.intentId(),
                    intent.activity(),
                    "SUPERSEDED");
        }
        intent = DiscretionaryIntent.pending(winner, utility, runnerUp, gameTime);
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.INTENT,
                intent.intentId(),
                winner,
                "utility=" + utility);
    }

    private boolean canSwitchTo(DiscretionaryActivity winner, float winnerUtility, long gameTime) {
        if (incumbentActivity == null) {
            return true;
        }
        if (incumbentActivity == winner) {
            return true;
        }
        if (intent != null && intent.isWithinCommitment(gameTime)) {
            return false;
        }
        return winnerUtility >= incumbentUtility + DiscretionaryDirectorConstants.SWITCH_MARGIN;
    }

    private void updateYieldRequests(DiscretionaryActivity winner, float winnerUtility, long gameTime) {
        if (incumbentActivity == null || incumbentActivity == winner) {
            return;
        }
        if (!canSwitchTo(winner, winnerUtility, gameTime)) {
            return;
        }
        if (incumbentActivity == DiscretionaryActivity.REST && winner == DiscretionaryActivity.EXPLORE) {
            restYieldRequested = true;
        } else if (incumbentActivity == DiscretionaryActivity.EXPLORE
                && winner == DiscretionaryActivity.REST) {
            exploreYieldRequested = true;
        }
    }

    private void expirePendingIfNeeded(long gameTime) {
        if (intent != null && intent.isExpiredPending(gameTime)) {
            intent.markTerminal(IntentLifecycle.EXPIRED, InvalidationCause.NONE);
            trace.record(
                    gameTime,
                    OpinionDecisionTrace.Stage.TERMINAL,
                    intent.intentId(),
                    intent.activity(),
                    "EXPIRED pending TTL");
            intent = null;
        }
    }

    private void invalidateActive(IntentLifecycle terminal, InvalidationCause cause, long gameTime) {
        if (intent != null && intent.isActive()) {
            markTerminal(terminal, cause, gameTime, cause.name());
        }
        incumbentActivity = null;
        incumbentUtility = 0f;
    }

    private void clearNonRunningIntent(IntentLifecycle terminal, InvalidationCause cause, long gameTime) {
        if (intent != null && intent.lifecycle() != IntentLifecycle.RUNNING) {
            markTerminal(terminal, cause, gameTime, "abstain");
        }
    }

    private void recordScores(ScoringResult scoring, long gameTime) {
        for (ActivityUtilityBreakdown breakdown : scoring.ranked()) {
            trace.record(
                    gameTime,
                    OpinionDecisionTrace.Stage.SCORE,
                    intent == null ? null : intent.intentId(),
                    breakdown.activity(),
                    breakdown.total()
                            + " pref="
                            + breakdown.preference()
                            + " rep="
                            + breakdown.repetition());
        }
    }

    private static float runnerUpUtility(ScoringResult scoring) {
        if (scoring.ranked().size() < 2) {
            return Float.NEGATIVE_INFINITY;
        }
        return scoring.ranked().get(1).total();
    }

    /** Test seam — seed incumbent for switch/yield scenarios. */
    void seedIncumbent(DiscretionaryActivity activity, float utility, long gameTime) {
        incumbentActivity = activity;
        incumbentUtility = utility;
        intent = DiscretionaryIntent.pending(activity, utility, 0f, gameTime);
        intent.markAdopted(gameTime);
        intent.markRunning();
    }

    void clearForTest() {
        intent = null;
        incumbentActivity = null;
        incumbentUtility = 0f;
        restYieldRequested = false;
        exploreYieldRequested = false;
        trace.clear();
    }

    public void onFreeze() {
        invalidateActive(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, 0L);
    }
}
