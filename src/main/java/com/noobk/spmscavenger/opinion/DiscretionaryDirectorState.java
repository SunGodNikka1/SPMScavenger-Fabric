package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityObservationService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-4 — per-mob discretionary director state and tick logic.
 *
 * <p>Running (incumbent) and pending (challenger) intents are stored separately so yield callbacks
 * cannot terminal the wrong authority.
 */
public final class DiscretionaryDirectorState {

    private DiscretionaryIntent runningIntent;
    private DiscretionaryIntent pendingIntent;
    private DiscretionaryActivity incumbentActivity;
    private RestAuthorityPhase restAuthorityPhase = RestAuthorityPhase.NONE;
    private boolean restYieldRequested;
    private boolean exploreYieldRequested;
    private final OpinionDecisionTrace trace = new OpinionDecisionTrace();

    public Optional<DiscretionaryIntent> intent() {
        if (pendingIntent != null && pendingIntent.isActive()) {
            return Optional.of(pendingIntent);
        }
        return Optional.ofNullable(runningIntent).filter(DiscretionaryIntent::isActive);
    }

    public Optional<DiscretionaryIntent> runningIntent() {
        return Optional.ofNullable(runningIntent);
    }

    public Optional<DiscretionaryIntent> pendingIntent() {
        return Optional.ofNullable(pendingIntent);
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

    public RestAuthorityPhase restAuthorityPhase() {
        return restAuthorityPhase;
    }

    public void tick(DirectorTickInput input) {
        Objects.requireNonNull(input, "input");
        restYieldRequested = false;
        exploreYieldRequested = false;

        if (!input.opinionEnabled()) {
            invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.OPINION_DISABLED, input.gameTime());
            return;
        }
        if (input.frozen()) {
            invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, input.gameTime());
            return;
        }

        InvalidationCause mandatory = DiscretionaryEligibility.invalidationForObservation(
                input.observation(), input.combatTarget());
        if (mandatory != InvalidationCause.NONE) {
            invalidateAll(IntentLifecycle.INVALIDATED, mandatory, input.gameTime());
            return;
        }

        expirePendingIfNeeded(input.gameTime());

        Optional<ScoringResult> scoring = scoreWithExploreAdoptionGate(input);
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
                    traceIntentId(),
                    top.activity(),
                    "top=" + top.total());
            clearPending(IntentLifecycle.ABSTAINED, InvalidationCause.NONE, input.gameTime(), "abstain");
            return;
        }

        trace.record(
                input.gameTime(),
                OpinionDecisionTrace.Stage.SELECT,
                traceIntentId(),
                top.activity(),
                "top=" + top.total() + " runnerUp=" + runnerUp);

        DiscretionaryActivity winner = top.activity();
        if (!canSwitchTo(winner, top.total(), scoring.get(), input.gameTime())) {
            updateYieldRequests(winner, top.total(), scoring.get(), input.gameTime());
            return;
        }

        if (needsPendingIssue(winner, input.gameTime())) {
            issuePending(winner, top.total(), runnerUp, input.gameTime());
        }

        updateYieldRequests(winner, top.total(), scoring.get(), input.gameTime());
    }

    public boolean hasRunningActionableIntent(DiscretionaryActivity activity) {
        return runningIntent != null
                && runningIntent.isActive()
                && runningIntent.activity() == activity;
    }

    public boolean hasActionableIntent(DiscretionaryActivity activity) {
        if (pendingIntent != null
                && pendingIntent.isActive()
                && pendingIntent.activity() == activity) {
            return true;
        }
        return runningIntent != null
                && runningIntent.isActive()
                && runningIntent.activity() == activity;
    }

    public void adopt(DiscretionaryActivity activity, long gameTime) {
        DiscretionaryIntent target = resolveAdoptTarget(activity);
        if (target == null) {
            return;
        }
        if (target == pendingIntent) {
            runningIntent = pendingIntent;
            pendingIntent = null;
        }
        target.markAdopted(gameTime);
        incumbentActivity = activity;
        if (activity == DiscretionaryActivity.REST) {
            restAuthorityPhase = RestAuthorityPhase.DELIVERY;
        }
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.ADOPT,
                target.intentId(),
                activity,
                "utility=" + target.selectedUtility());
    }

    public void markRunning(DiscretionaryActivity activity, long gameTime) {
        DiscretionaryIntent target = runningIntent;
        if (target == null || target.activity() != activity) {
            return;
        }
        target.markRunning();
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.EXECUTOR,
                target.intentId(),
                activity,
                "running");
    }

    public void markRestClaimOpened(long gameTime) {
        if (runningIntent != null && runningIntent.activity() == DiscretionaryActivity.REST) {
            restAuthorityPhase = RestAuthorityPhase.CLAIMED;
            trace.record(
                    gameTime,
                    OpinionDecisionTrace.Stage.EXECUTOR,
                    runningIntent.intentId(),
                    DiscretionaryActivity.REST,
                    "rest-claim-opened");
        }
    }

    public void markRestDeliveryComplete(long gameTime) {
        if (restAuthorityPhase == RestAuthorityPhase.DELIVERY) {
            restAuthorityPhase = RestAuthorityPhase.DELIVERY_COMPLETE;
        }
    }

    public void markYield(
            UUID releasedIntentId,
            DiscretionaryActivity released,
            DiscretionaryActivity adopted,
            long gameTime) {
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.YIELD,
                releasedIntentId,
                adopted,
                "released=" + released);
    }

    public void markTerminalForIntent(
            UUID intentId,
            IntentLifecycle terminal,
            InvalidationCause cause,
            long gameTime,
            String detail) {
        if (intentId == null) {
            return;
        }
        if (pendingIntent != null && pendingIntent.intentId().equals(intentId)) {
            terminalize(pendingIntent, terminal, cause, gameTime, detail);
            pendingIntent = null;
            return;
        }
        if (runningIntent != null && runningIntent.intentId().equals(intentId)) {
            terminalize(runningIntent, terminal, cause, gameTime, detail);
            if (runningIntent.activity() == DiscretionaryActivity.REST) {
                restAuthorityPhase = RestAuthorityPhase.NONE;
            }
            runningIntent = null;
            incumbentActivity = null;
        }
    }

    public void markTerminal(
            IntentLifecycle terminal, InvalidationCause cause, long gameTime, String detail) {
        if (pendingIntent != null && pendingIntent.isActive()) {
            terminalize(pendingIntent, terminal, cause, gameTime, detail);
            pendingIntent = null;
        }
        if (runningIntent != null && runningIntent.isActive()) {
            terminalize(runningIntent, terminal, cause, gameTime, detail);
            if (runningIntent.activity() == DiscretionaryActivity.REST) {
                restAuthorityPhase = RestAuthorityPhase.NONE;
            }
            runningIntent = null;
            incumbentActivity = null;
        }
    }

    private DiscretionaryIntent resolveAdoptTarget(DiscretionaryActivity activity) {
        if (pendingIntent != null
                && pendingIntent.isActive()
                && pendingIntent.activity() == activity) {
            return pendingIntent;
        }
        if (runningIntent != null
                && runningIntent.isActive()
                && runningIntent.activity() == activity) {
            return runningIntent;
        }
        return null;
    }

    private boolean needsPendingIssue(DiscretionaryActivity winner, long gameTime) {
        if (pendingIntent != null
                && pendingIntent.isActive()
                && pendingIntent.activity() == winner
                && !pendingIntent.isExpiredPending(gameTime)) {
            return false;
        }
        if (runningIntent != null
                && runningIntent.isActive()
                && runningIntent.activity() == winner
                && pendingIntent == null) {
            return false;
        }
        return true;
    }

    private void issuePending(
            DiscretionaryActivity winner, float utility, float runnerUp, long gameTime) {
        if (pendingIntent != null && pendingIntent.isActive()) {
            terminalize(pendingIntent, IntentLifecycle.INTERRUPTED, InvalidationCause.SUPERSEDED, gameTime, "superseded-pending");
            pendingIntent = null;
        }
        pendingIntent = DiscretionaryIntent.pending(winner, utility, runnerUp, gameTime);
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.INTENT,
                pendingIntent.intentId(),
                winner,
                "utility=" + utility);
    }

    private boolean canSwitchTo(
            DiscretionaryActivity winner,
            float winnerUtility,
            ScoringResult scoring,
            long gameTime) {
        if (incumbentActivity == null) {
            return true;
        }
        if (incumbentActivity == winner) {
            return true;
        }
        if (runningIntent != null && runningIntent.isWithinCommitment(gameTime)) {
            return false;
        }
        float currentIncumbentUtility = utilityFor(scoring, incumbentActivity);
        return winnerUtility >= currentIncumbentUtility + DiscretionaryDirectorConstants.SWITCH_MARGIN;
    }

    private void updateYieldRequests(
            DiscretionaryActivity winner,
            float winnerUtility,
            ScoringResult scoring,
            long gameTime) {
        if (incumbentActivity == null || incumbentActivity == winner) {
            return;
        }
        if (!canSwitchTo(winner, winnerUtility, scoring, gameTime)) {
            return;
        }
        if (incumbentActivity == DiscretionaryActivity.REST && winner == DiscretionaryActivity.EXPLORE) {
            restYieldRequested = true;
        } else if (incumbentActivity == DiscretionaryActivity.EXPLORE
                && winner == DiscretionaryActivity.REST) {
            exploreYieldRequested = true;
        }
    }

    private static float utilityFor(ScoringResult scoring, DiscretionaryActivity activity) {
        return scoring.ranked().stream()
                .filter(breakdown -> breakdown.activity() == activity)
                .map(ActivityUtilityBreakdown::total)
                .findFirst()
                .orElse(Float.NEGATIVE_INFINITY);
    }

    private void expirePendingIfNeeded(long gameTime) {
        if (pendingIntent != null && pendingIntent.isExpiredPending(gameTime)) {
            terminalize(pendingIntent, IntentLifecycle.EXPIRED, InvalidationCause.NONE, gameTime, "EXPIRED pending TTL");
            pendingIntent = null;
        }
    }

    private static Optional<ScoringResult> scoreWithExploreAdoptionGate(DirectorTickInput input) {
        Optional<ScoringResult> scoring = IdleOpportunityPolicy.score(input.scoringInput());
        if (scoring.isEmpty() || input.exploreAdoptionReady()) {
            return scoring;
        }
        var filtered = scoring.get().ranked().stream()
                .filter(breakdown -> breakdown.activity() != DiscretionaryActivity.EXPLORE)
                .toList();
        return filtered.isEmpty() ? Optional.empty() : Optional.of(ScoringResult.of(filtered));
    }

    private void invalidateAll(IntentLifecycle terminal, InvalidationCause cause, long gameTime) {
        if (pendingIntent != null && pendingIntent.isActive()) {
            terminalize(pendingIntent, terminal, cause, gameTime, cause.name());
            pendingIntent = null;
        }
        if (runningIntent != null && runningIntent.isActive()) {
            terminalize(runningIntent, terminal, cause, gameTime, cause.name());
            runningIntent = null;
        }
        incumbentActivity = null;
        restAuthorityPhase = RestAuthorityPhase.NONE;
    }

    private void clearPending(
            IntentLifecycle terminal, InvalidationCause cause, long gameTime, String detail) {
        if (pendingIntent != null) {
            terminalize(pendingIntent, terminal, cause, gameTime, detail);
            pendingIntent = null;
        }
    }

    private void terminalize(
            DiscretionaryIntent target,
            IntentLifecycle terminal,
            InvalidationCause cause,
            long gameTime,
            String detail) {
        target.markTerminal(terminal, cause);
        trace.record(
                gameTime,
                OpinionDecisionTrace.Stage.TERMINAL,
                target.intentId(),
                target.activity(),
                terminal + (detail == null || detail.isBlank() ? "" : ":" + detail));
    }

    private UUID traceIntentId() {
        if (pendingIntent != null) {
            return pendingIntent.intentId();
        }
        if (runningIntent != null) {
            return runningIntent.intentId();
        }
        return null;
    }

    private void recordScores(ScoringResult scoring, long gameTime) {
        for (ActivityUtilityBreakdown breakdown : scoring.ranked()) {
            trace.record(
                    gameTime,
                    OpinionDecisionTrace.Stage.SCORE,
                    traceIntentId(),
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

    /** Test seam — seed running incumbent for switch/yield scenarios. */
    void seedIncumbent(DiscretionaryActivity activity, float utility, long gameTime) {
        incumbentActivity = activity;
        runningIntent = DiscretionaryIntent.pending(activity, utility, 0f, gameTime);
        runningIntent.markAdopted(Math.max(1L, gameTime));
        runningIntent.markRunning();
        if (activity == DiscretionaryActivity.REST) {
            restAuthorityPhase = RestAuthorityPhase.DELIVERY;
        }
    }

    void clearForTest() {
        runningIntent = null;
        pendingIntent = null;
        incumbentActivity = null;
        restAuthorityPhase = RestAuthorityPhase.NONE;
        restYieldRequested = false;
        exploreYieldRequested = false;
        trace.clear();
    }

    public void onFreeze() {
        invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, 0L);
    }

    public void clearForUnload() {
        clearForTest();
    }
}
