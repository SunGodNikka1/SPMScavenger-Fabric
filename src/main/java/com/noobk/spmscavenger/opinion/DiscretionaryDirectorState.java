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
    /**
     * D-GAO-051 — one identity-bound yield contract instead of a boolean per activity pair.
     *
     * <p>Two flags described one concept and grew quadratically; worse, a bare boolean carried no
     * identity, so a request raised against one execution could be answered by whatever happened to
     * be running when the executor next looked.
     */
    private YieldRequest yieldRequest;
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

    public Optional<YieldRequest> yieldRequest() {
        return Optional.ofNullable(yieldRequest);
    }

    /**
     * Whether the execution currently running this activity has been asked to yield.
     *
     * <p>Identity-bound: a stale request cannot yield a replacement intent, even for the same
     * activity at the same place.
     */
    public boolean mustYield(DiscretionaryActivity activity, long now) {
        if (yieldRequest == null || !yieldRequest.isFor(activity)) {
            return false;
        }
        DiscretionaryIntent incumbent = runningIntent;
        if (!yieldRequest.appliesTo(incumbent, now)) {
            finishYieldRequest(
                    yieldRequest.expired(now)
                            ? YieldOutcome.EXPIRED
                            : YieldOutcome.STALE_INCUMBENT,
                    now);
            return false;
        }
        return true;
    }

    /**
     * @param decisionId the decision that <b>selected the challenger</b> — the causal origin of this
     *     switch. Previously this recorded {@code runningIntent.decisionId()}, the decision that
     *     created the <em>incumbent</em>, so a yield raised at decision #87 claimed to originate at
     *     #20 and any trace built on it would attach the switch to the wrong historical cause.
     */
    void requestYield(long decisionId, DiscretionaryActivity challenger, long now) {
        if (runningIntent == null || !runningIntent.isActive()) {
            return;
        }
        yieldRequest = YieldRequest.of(runningIntent, challenger, decisionId, now);
        trace.recordYieldEvent(YieldEvent.requested(yieldRequest, now));
    }

    /**
     * D-GAO-051 — an executor reports reaching its safe yield point; the director resolves the rest.
     *
     * <p>Validated against the live request rather than trusted: the acknowledgement must name an
     * execution that is actually the incumbent of an unexpired request for that activity. A stale
     * or mismatched acknowledgement is ignored, so a replaced intent cannot be terminalized by an
     * older executor finishing late.
     *
     * @return whether the yield was accepted and completed
     */
    boolean acknowledgeYield(
            UUID releasingIntentId, DiscretionaryActivity releasingActivity, long gameTime) {
        if (yieldRequest == null
                || !yieldRequest.isFor(releasingActivity)
                || !yieldRequest.incumbentIntentId().equals(releasingIntentId)
                || yieldRequest.expired(gameTime)) {
            return false;
        }
        DiscretionaryActivity challenger = yieldRequest.challengerActivity();
        markYield(
                releasingIntentId,
                releasingActivity,
                challenger,
                yieldRequest.originDecisionId(),
                gameTime);
        markTerminalForIntent(
                releasingIntentId,
                IntentLifecycle.INTERRUPTED,
                InvalidationCause.SUPERSEDED,
                gameTime,
                "yield-" + challenger.name().toLowerCase(java.util.Locale.ROOT));
        finishYieldRequest(YieldOutcome.ACKNOWLEDGED, gameTime);
        return true;
    }

    /**
     * D-GAO-051 — every path that removes a {@link YieldRequest} goes through here.
     *
     * <p>There were five: stale detection on read, expiry on tick, successful acknowledgement,
     * explicit clearing, and mandatory invalidation (which silently left the request behind for a
     * later read to notice). Five branches meant a trace would need five call sites and they would
     * drift. One seam means the causal event is emitted exactly once, at the moment it happens.
     *
     * @return the request that ended, for callers that need its identity
     */
    java.util.Optional<YieldRequest> finishYieldRequest(YieldOutcome outcome, long gameTime) {
        YieldRequest ending = yieldRequest;
        if (ending == null) {
            return java.util.Optional.empty();
        }
        yieldRequest = null;
        lastYieldOutcome = outcome;
        lastYieldOutcomeAt = gameTime;
        // Emitted from the ending request itself, so the historical evidence is the transaction and
        // lastYieldOutcome stays what it was meant to be - inspector convenience, not the source of
        // truth. One seam means one emission point that cannot drift.
        trace.recordYieldEvent(YieldEvent.ended(ending, outcome, gameTime));
        return java.util.Optional.of(ending);
    }

    /** How a yield request ended. Distinct from a decision disposition on purpose. */
    public enum YieldOutcome {
        /** The incumbent executor reached its safe yield point and reported it. */
        ACKNOWLEDGED,
        /** Nobody answered within the request's bounded lifetime. */
        EXPIRED,
        /** The incumbent it named is no longer the running execution. */
        STALE_INCUMBENT,
        /** Survival, combat, command or freeze ended the negotiation outright. */
        MANDATORY_INVALIDATION,
        /** A later decision replaced it. */
        SUPERSEDED
    }

    private YieldOutcome lastYieldOutcome;
    private long lastYieldOutcomeAt;

    public java.util.Optional<YieldOutcome> lastYieldOutcome() {
        return java.util.Optional.ofNullable(lastYieldOutcome);
    }

    public long lastYieldOutcomeAt() {
        return lastYieldOutcomeAt;
    }

    void clearYieldRequest() {
        finishYieldRequest(YieldOutcome.SUPERSEDED, lastGameTime);
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
        // A request does not survive the tick that raised it being superseded; expiry and
        // identity are checked on read, so nothing here can yield the wrong execution.
        if (yieldRequest != null && yieldRequest.expired(input.gameTime())) {
            finishYieldRequest(YieldOutcome.EXPIRED, input.gameTime());
        }

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

        // Exactly one reconciliation per completed evaluation, whatever it concluded.
        reconcileYieldTransaction(decide(input), input.gameTime());
    }

    /**
     * D-GAO-051 — one discretionary evaluation, producing exactly one desired-yield result.
     *
     * <p>Extracted because the reconciler was only reachable on the successful path.
     * {@code NO_CANDIDATES}, {@code BELOW_ACTIVATION_THRESHOLD}, {@code COMMITMENT_HOLD} and
     * {@code SWITCH_MARGIN_HOLD} each returned early, so a live request survived a decision
     * that had explicitly declined to switch — the same stale preference authority, reached
     * by four other doors.
     *
     * <p>Scattering a clear before each return would have restored the multi-owner lifecycle
     * the single seam removed. Instead every exit states what it authorizes, and
     * {@link #tick} reconciles once.
     */
    private YieldDirective decide(DirectorTickInput input) {
        expirePendingIfNeeded(input.gameTime());

        ScoringEvaluation evaluation = evaluateCandidates(input, incumbentActivity);
        long decisionId = trace.beginDecision(input.gameTime(), evaluation.candidates());
        if (evaluation.scoring().isEmpty()) {
            trace.conclude(
                    decisionId,
                    OpinionDecisionTrace.DecisionDisposition.NO_CANDIDATES,
                    null,
                    true);
            return YieldDirective.none();
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
            return YieldDirective.none();
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
            return YieldDirective.none();
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

        return YieldDirective.switchTo(decisionId, winner, top.total(), scoring);
    }

    /** What a completed evaluation authorizes: nothing, or one specific switch. */
    private record YieldDirective(
            boolean wantsSwitch,
            long decisionId,
            DiscretionaryActivity challenger,
            float challengerUtility,
            ScoringResult scoring) {

        static YieldDirective none() {
            return new YieldDirective(false, 0L, null, 0f, null);
        }

        static YieldDirective switchTo(
                long decisionId, DiscretionaryActivity challenger, float utility,
                ScoringResult scoring) {
            return new YieldDirective(true, decisionId, challenger, utility, scoring);
        }
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

    /**
     * @param originDecisionId the decision that selected the challenger. The switch belongs to that
     *     decision; the incumbent's own creation decision still receives its terminal lifecycle, so
     *     "why did this change" and "what happened to that intent" stay two separate truths.
     */
    public void markYield(
            UUID releasedIntentId,
            DiscretionaryActivity released,
            DiscretionaryActivity adopted,
            long originDecisionId,
            long gameTime) {
        DiscretionaryIntent releasedIntent = intentById(releasedIntentId);
        if (releasedIntent == null) {
            return;
        }
        trace.transition(
                originDecisionId > 0 ? originDecisionId : releasedIntent.decisionId(),
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

    /**
     * D-GAO-051 — reconcile the desired switch with the request already in flight.
     *
     * <p>Two defects this replaces. First, raising a fresh request on every qualifying decision
     * refreshed {@code requestedAt}, {@code expiresAt} and {@code originDecisionId} every 10 ticks:
     * the 200-tick bound became an immortal sliding timeout, and the causal origin drifted to
     * whichever repeated scoring pass ran last rather than the decision that actually chose to
     * switch. Second, when the incumbent won again the old early return left the request live, so an
     * executor could still yield to a challenger the latest decision had already rejected — stale
     * preference authority even though the incumbent identity was current.
     *
     * <p>A yield is a <b>transaction</b>: it starts once, keeps its identity and its clock while the
     * director still wants it, and ends exactly once with a named reason.
     */
    /**
     * D-GAO-051 — reconcile the live transaction with what the latest evaluation authorizes.
     *
     * <p>A yield is a <b>transaction</b>: one start, one identity, one clock, one named ending. Two
     * defects this replaces. Raising a fresh request on every qualifying decision refreshed
     * {@code requestedAt}, {@code expiresAt} and {@code originDecisionId} every 10 ticks, so the
     * 200-tick bound became an immortal sliding timeout and the causal origin drifted to whichever
     * scoring pass ran last. And when the director stopped wanting the switch, the request stayed
     * live, so an executor could yield to a challenger the latest decision had already rejected.
     */
    private void reconcileYieldTransaction(YieldDirective directive, long gameTime) {
        boolean wantsSwitch = directive.wantsSwitch()
                && incumbentActivity != null
                && incumbentActivity != directive.challenger()
                && canSwitchTo(
                        directive.challenger(),
                        directive.challengerUtility(),
                        directive.scoring(),
                        gameTime);

        if (!wantsSwitch) {
            // Covers every conclusion that authorizes no switch - retained incumbent, margin hold,
            // commitment hold, below threshold, no candidates. An obsolete challenger must not
            // remain executable just because the decision ended down a different branch.
            finishYieldRequest(YieldOutcome.SUPERSEDED, gameTime);
            return;
        }
        if (yieldRequest == null) {
            requestYield(directive.decisionId(), directive.challenger(), gameTime);
            return;
        }
        if (yieldRequest.expired(gameTime)) {
            // Ends as EXPIRED, not SUPERSEDED: nobody answered in time. This evaluation
            // independently still wants the switch, so a new bounded transaction may begin.
            finishYieldRequest(YieldOutcome.EXPIRED, gameTime);
            requestYield(directive.decisionId(), directive.challenger(), gameTime);
            return;
        }
        boolean sameTransaction = runningIntent != null
                && yieldRequest.incumbentIntentId().equals(runningIntent.intentId())
                && yieldRequest.challengerActivity() == directive.challenger();
        if (sameTransaction) {
            // Deliberately untouched. Refreshing here is what made the lifetime unbounded.
            return;
        }
        finishYieldRequest(YieldOutcome.SUPERSEDED, gameTime);
        requestYield(directive.decisionId(), directive.challenger(), gameTime);
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

    /**
     * D-GAO-050 — adoption failure must not delete a running incumbent from the comparison.
     *
     * <p>Previously a candidate whose {@code adoptionReady} was false was suppressed outright. For a
     * <em>running</em> activity that is the wrong question: an adoption cooldown says nothing about
     * whether the live expedition is still valid. The incumbent vanished from scoring, so a
     * challenger beat nothing instead of beating the incumbent's real utility, and the mob abandoned
     * a healthy expedition to a rival that would have lost a fair comparison.
     *
     * <p>An incumbent therefore stays eligible when {@code adoptionReady == false} but
     * {@code continuationValid == true}. It still has to win on utility like anything else.
     */
    private static ScoringEvaluation evaluateCandidates(
            DirectorTickInput input, DiscretionaryActivity incumbent) {
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
            ActivityContinuation continuation = input.continuations().forActivity(activity);
            boolean isIncumbent = incumbent == activity;
            boolean retained = !admission.adoptionReady()
                    && retainsIncumbent(input, incumbent, activity);
            ExecutionEvidence evidence =
                    ExecutionEvidence.of(admission, continuation, isIncumbent, retained);
            if (!admission.executorPresent()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        null,
                        OpinionDecisionTrace.SuppressionReason.EXECUTOR_UNAVAILABLE,
                        "",
                        evidence));
            } else if (!input.scoringInput().discretionaryEligible()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        breakdown,
                        OpinionDecisionTrace.SuppressionReason.DISCRETIONARY_CONTEXT_BLOCKED,
                        "",
                        evidence));
            } else if (!admission.adoptionReady()
                    && retainsIncumbent(input, incumbent, activity)
                    && breakdown != null) {
                // Not adoptable, but running and continuable: it competes on its real utility.
                candidates.add(OpinionDecisionTrace.Candidate.eligible(breakdown, evidence));
                eligible.add(breakdown);
            } else if (!admission.adoptionReady()) {
                candidates.add(OpinionDecisionTrace.Candidate.suppressed(
                        activity,
                        breakdown,
                        OpinionDecisionTrace.SuppressionReason.ADOPTION_NOT_READY,
                        admission.suppressionDetail(),
                        evidence));
            } else if (breakdown != null) {
                candidates.add(OpinionDecisionTrace.Candidate.eligible(breakdown, evidence));
                eligible.add(breakdown);
            }
        }

        Optional<ScoringResult> scoring = eligible.isEmpty()
                ? Optional.empty()
                : Optional.of(ScoringResult.of(eligible));
        return new ScoringEvaluation(scoring, java.util.List.copyOf(candidates));
    }

    /** True when this activity is the running incumbent and its continuation is still valid. */
    private static boolean retainsIncumbent(
            DirectorTickInput input, DiscretionaryActivity incumbent,
            DiscretionaryActivity activity) {
        return incumbent != null
                && incumbent == activity
                && input.continuations().forActivity(activity).continuable();
    }

    private void invalidateAll(IntentLifecycle terminal, InvalidationCause cause, long gameTime) {
        // Mandatory authority does not negotiate. Leaving the request behind meant a later read
        // discovered its incumbent had vanished and reported STALE - true but misleading, since the
        // real cause was combat or a player command.
        finishYieldRequest(YieldOutcome.MANDATORY_INVALIDATION, gameTime);
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
        yieldRequest = null;
        lastYieldOutcome = null;
        lastYieldOutcomeAt = 0L;
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
