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
    private long lastGameTime;
    /**
     * GAO-4R — last-tick adoption diagnostics for inspector readout only.
     *
     * <p>Do not extend with per-subsystem fields; candidate {@code suppressionDetail} is the scalable
     * direction for GAO-10+.
     */
    private ActivityAdmissions lastAdmissions = ActivityAdmissions.unavailable();
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

    public long lastGameTime() {
        return lastGameTime;
    }

    public ActivityAdmissions lastAdmissions() {
        return lastAdmissions;
    }

    public void tick(DirectorTickInput input) {
        Objects.requireNonNull(input, "input");
        lastGameTime = input.gameTime();
        lastAdmissions = input.admissions();
        restYieldRequested = false;
        exploreYieldRequested = false;

        if (!input.opinionEnabled()) {
            long decisionId = trace.beginDecision(input.gameTime(), java.util.List.of());
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.OPINION_DISABLED,
                    InvalidationCause.OPINION_DISABLED,
                    null,
                    true);
            invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.OPINION_DISABLED, input.gameTime());
            return;
        }
        if (input.frozen()) {
            long decisionId = trace.beginDecision(input.gameTime(), java.util.List.of());
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.FROZEN,
                    InvalidationCause.UNLOAD_FREEZE,
                    null,
                    true);
            invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, input.gameTime());
            return;
        }

        InvalidationCause mandatory = DiscretionaryEligibility.invalidationForObservation(
                input.observation(), input.combatTarget());
        if (mandatory != InvalidationCause.NONE) {
            long decisionId = trace.beginDecision(input.gameTime(), java.util.List.of());
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY,
                    mandatory,
                    null,
                    true);
            invalidateAll(IntentLifecycle.INVALIDATED, mandatory, input.gameTime());
            return;
        }

        expirePendingIfNeeded(input.gameTime());

        ScoringEvaluation evaluation = evaluateCandidates(input);
        long decisionId = trace.beginDecision(input.gameTime(), evaluation.candidates());
        if (evaluation.scoring().isEmpty()) {
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.NO_CANDIDATES,
                    null,
                    true);
            return;
        }

        ScoringResult scoring = evaluation.scoring().orElseThrow();
        ActivityUtilityBreakdown top = scoring.top().orElseThrow();
        float runnerUp = runnerUpUtility(scoring);

        if (top.total() < DiscretionaryDirectorConstants.ACTIVATION_THRESHOLD) {
            trace.transition(
                    decisionId,
                    input.gameTime(),
                    OpinionDecisionTrace.Stage.ABSTAIN,
                    null,
                    top.activity(),
                    null,
                    InvalidationCause.NONE,
                    "top=" + top.total());
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.BELOW_ACTIVATION_THRESHOLD,
                    top.activity(),
                    true);
            clearPending(IntentLifecycle.ABSTAINED, InvalidationCause.NONE, input.gameTime(), "abstain");
            return;
        }

        trace.transition(
                decisionId,
                input.gameTime(),
                OpinionDecisionTrace.Stage.SELECT,
                null,
                top.activity(),
                null,
                InvalidationCause.NONE,
                "top=" + top.total() + " runnerUp=" + runnerUp);

        DiscretionaryActivity winner = top.activity();
        SwitchBlocker switchBlocker = switchBlocker(winner, top.total(), scoring, input.gameTime());
        if (switchBlocker != SwitchBlocker.NONE) {
            trace.conclude(
                    decisionId,
                    switchBlocker == SwitchBlocker.COMMITMENT
                            ? OpinionDecisionTrace.DecisionDisposition.COMMITMENT_HOLD
                            : OpinionDecisionTrace.DecisionDisposition.SWITCH_MARGIN_HOLD,
                    winner,
                    true);
            return;
        }

        if (needsPendingIssue(winner, input.gameTime())) {
            issuePending(decisionId, winner, top.total(), runnerUp, input.gameTime());
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.INTENT_ISSUED,
                    winner,
                    false);
        } else {
            DiscretionaryIntent existing = activeIntentFor(winner);
            if (existing == null) {
                recoverRetainedIntentInvariantViolation(
                        decisionId,
                        winner,
                        top.total(),
                        runnerUp,
                        input.gameTime(),
                        "needsPendingIssue=false but activeIntentFor returned null");
            } else {
                Optional<OpinionDecisionTrace.DecisionDisposition> retained =
                        retainedDisposition(existing);
                if (retained.isEmpty()) {
                    recoverRetainedIntentInvariantViolation(
                            decisionId,
                            winner,
                            top.total(),
                            runnerUp,
                            input.gameTime(),
                            "active intent lifecycle not retainable: " + existing.lifecycle());
                } else {
                    trace.attachIntent(decisionId, existing.intentId());
                    trace.conclude(decisionId, retained.get(), winner, true);
                }
            }
        }

        updateYieldRequests(winner, top.total(), scoring, input.gameTime());
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
        trace.transition(
                target.decisionId(),
                gameTime,
                OpinionDecisionTrace.Stage.ADOPT,
                target.intentId(),
                activity,
                target.lifecycle(),
                InvalidationCause.NONE,
                "utility=" + target.selectedUtility());
    }

    public void markRunning(DiscretionaryActivity activity, long gameTime) {
        DiscretionaryIntent target = runningIntent;
        if (target == null || target.activity() != activity) {
            return;
        }
        target.markRunning();
        trace.transition(
                target.decisionId(),
                gameTime,
                OpinionDecisionTrace.Stage.EXECUTOR,
                target.intentId(),
                activity,
                target.lifecycle(),
                InvalidationCause.NONE,
                "running");
    }

    public void markRestClaimOpened(long gameTime) {
        if (runningIntent != null && runningIntent.activity() == DiscretionaryActivity.REST) {
            restAuthorityPhase = RestAuthorityPhase.CLAIMED;
            trace.transition(
                    runningIntent.decisionId(),
                    gameTime,
                    OpinionDecisionTrace.Stage.CLAIM,
                    runningIntent.intentId(),
                    DiscretionaryActivity.REST,
                    runningIntent.lifecycle(),
                    InvalidationCause.NONE,
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
        DiscretionaryIntent releasedIntent = intentById(releasedIntentId);
        if (releasedIntent == null) {
            return;
        }
        trace.transition(
                releasedIntent.decisionId(),
                gameTime,
                OpinionDecisionTrace.Stage.YIELD,
                releasedIntentId,
                released,
                releasedIntent.lifecycle(),
                InvalidationCause.NONE,
                "adopted=" + adopted);
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

    public void recordLearningForIntent(
            UUID intentId, OpinionDecisionTrace.LearningOutcome learningOutcome) {
        DiscretionaryIntent target = intentById(intentId);
        if (target != null) {
            trace.recordLearning(target.decisionId(), learningOutcome);
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
            long decisionId,
            DiscretionaryActivity winner,
            float utility,
            float runnerUp,
            long gameTime) {
        if (pendingIntent != null && pendingIntent.isActive()) {
            terminalize(pendingIntent, IntentLifecycle.INTERRUPTED, InvalidationCause.SUPERSEDED, gameTime, "superseded-pending");
            pendingIntent = null;
        }
        pendingIntent = DiscretionaryIntent.pending(decisionId, winner, utility, runnerUp, gameTime);
        trace.attachIntent(decisionId, pendingIntent.intentId());
        trace.transition(
                decisionId,
                gameTime,
                OpinionDecisionTrace.Stage.INTENT,
                pendingIntent.intentId(),
                winner,
                pendingIntent.lifecycle(),
                InvalidationCause.NONE,
                "utility=" + utility);
    }

    private boolean canSwitchTo(
            DiscretionaryActivity winner,
            float winnerUtility,
            ScoringResult scoring,
            long gameTime) {
        return switchBlocker(winner, winnerUtility, scoring, gameTime) == SwitchBlocker.NONE;
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

    private static Optional<OpinionDecisionTrace.DecisionDisposition> retainedDisposition(
            DiscretionaryIntent existing) {
        return switch (existing.lifecycle()) {
            case PENDING -> Optional.of(OpinionDecisionTrace.DecisionDisposition.PENDING_INTENT_RETAINED);
            case ADOPTED -> Optional.of(OpinionDecisionTrace.DecisionDisposition.ADOPTED_INTENT_RETAINED);
            case RUNNING -> Optional.of(OpinionDecisionTrace.DecisionDisposition.RUNNING_INTENT_RETAINED);
            default -> Optional.empty();
        };
    }

    /**
     * Records an explicit director invariant failure and re-issues pending intent so the mob is not
     * left without authority. Never masquerades as {@code PENDING_INTENT_RETAINED}.
     */
    private void recoverRetainedIntentInvariantViolation(
            long decisionId,
            DiscretionaryActivity winner,
            float utility,
            float runnerUp,
            long gameTime,
            String detail) {
        trace.transition(
                decisionId,
                gameTime,
                OpinionDecisionTrace.Stage.SELECT,
                null,
                winner,
                null,
                InvalidationCause.NONE,
                "DIRECTOR_INCONSISTENCY: " + detail);
        issuePending(decisionId, winner, utility, runnerUp, gameTime);
        trace.conclude(
                decisionId,
                OpinionDecisionTrace.DecisionDisposition.DIRECTOR_INCONSISTENCY,
                winner,
                false);
    }

    private static ScoringEvaluation evaluateCandidates(DirectorTickInput input) {
        Optional<ScoringResult> raw = IdleOpportunityPolicy.score(input.scoringInput());
        java.util.List<OpinionDecisionTrace.Candidate> candidates = new java.util.ArrayList<>();
        java.util.List<ActivityUtilityBreakdown> eligible = new java.util.ArrayList<>();

        for (DiscretionaryActivity activity : DiscretionaryActivity.values()) {
            ActivityUtilityBreakdown breakdown = raw.stream()
                    .flatMap(result -> result.ranked().stream())
                    .filter(candidate -> candidate.activity() == activity)
                    .findFirst()
                    .orElse(null);
            ActivityAdmission admission = input.admissions().forActivity(activity);
            if (!admission.executorPresent()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        null,
                        OpinionDecisionTrace.SuppressionReason.EXECUTOR_UNAVAILABLE));
            } else if (!input.scoringInput().discretionaryEligible()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        breakdown,
                        OpinionDecisionTrace.SuppressionReason.DISCRETIONARY_CONTEXT_BLOCKED));
            } else if (!admission.adoptionReady()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        breakdown,
                        OpinionDecisionTrace.SuppressionReason.ADOPTION_NOT_READY,
                        admission.suppressionDetail()));
            } else if (breakdown != null) {
                candidates.add(OpinionDecisionTrace.Candidate.eligible(breakdown));
                eligible.add(breakdown);
            }
        }

        Optional<ScoringResult> scoring = eligible.isEmpty()
                ? Optional.empty()
                : Optional.of(ScoringResult.of(eligible));
        return new ScoringEvaluation(scoring, java.util.List.copyOf(candidates));
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
        trace.transition(
                target.decisionId(),
                gameTime,
                OpinionDecisionTrace.Stage.TERMINAL,
                target.intentId(),
                target.activity(),
                terminal,
                cause,
                detail);
    }

    private DiscretionaryIntent activeIntentFor(DiscretionaryActivity activity) {
        if (pendingIntent != null && pendingIntent.isActive() && pendingIntent.activity() == activity) {
            return pendingIntent;
        }
        if (runningIntent != null && runningIntent.isActive() && runningIntent.activity() == activity) {
            return runningIntent;
        }
        return null;
    }

    private DiscretionaryIntent intentById(UUID intentId) {
        if (pendingIntent != null && pendingIntent.intentId().equals(intentId)) {
            return pendingIntent;
        }
        if (runningIntent != null && runningIntent.intentId().equals(intentId)) {
            return runningIntent;
        }
        return null;
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
        long decisionId = trace.beginDecision(gameTime, java.util.List.of());
        runningIntent = DiscretionaryIntent.pending(decisionId, activity, utility, 0f, gameTime);
        trace.attachIntent(decisionId, runningIntent.intentId());
        trace.conclude(
                decisionId,
                OpinionDecisionTrace.DecisionDisposition.INTENT_ISSUED,
                activity,
                false);
        trace.transition(
                decisionId,
                gameTime,
                OpinionDecisionTrace.Stage.INTENT,
                runningIntent.intentId(),
                activity,
                runningIntent.lifecycle(),
                InvalidationCause.NONE,
                "test-seed utility=" + utility);
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
        lastGameTime = 0L;
        lastAdmissions = ActivityAdmissions.unavailable();
        trace.clear();
    }

    public void onFreeze() {
        invalidateAll(IntentLifecycle.INVALIDATED, InvalidationCause.UNLOAD_FREEZE, 0L);
    }

    public void clearForUnload() {
        clearForTest();
    }

    private SwitchBlocker switchBlocker(
            DiscretionaryActivity winner,
            float winnerUtility,
            ScoringResult scoring,
            long gameTime) {
        if (incumbentActivity == null || incumbentActivity == winner) {
            return SwitchBlocker.NONE;
        }
        if (runningIntent != null && runningIntent.isWithinCommitment(gameTime)) {
            return SwitchBlocker.COMMITMENT;
        }
        float currentIncumbentUtility = utilityFor(scoring, incumbentActivity);
        return winnerUtility >= currentIncumbentUtility + DiscretionaryDirectorConstants.SWITCH_MARGIN
                ? SwitchBlocker.NONE
                : SwitchBlocker.MARGIN;
    }

    private enum SwitchBlocker {
        NONE,
        COMMITMENT,
        MARGIN
    }

    private record ScoringEvaluation(
            Optional<ScoringResult> scoring,
            java.util.List<OpinionDecisionTrace.Candidate> candidates) {}
}
