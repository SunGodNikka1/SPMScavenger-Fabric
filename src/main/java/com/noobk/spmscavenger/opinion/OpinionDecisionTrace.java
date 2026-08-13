package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.ExperienceCause;
import com.noobk.spmscavenger.experience.ExperienceKind;
import com.noobk.spmscavenger.experience.OutcomeClass;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

/**
 * GAO-8B Task 42A — bounded, whole-decision causal trace.
 *
 * <p>The trace owns copied values only. It never rescans state, scores an activity, or grants
 * scheduler authority. Capacity is measured in decisions; eviction always removes one whole
 * record, never individual transitions from a retained decision.
 */
public final class OpinionDecisionTrace {

    public enum Stage {
        SELECT,
        ABSTAIN,
        INTENT,
        YIELD,
        ADOPT,
        EXECUTOR,
        CLAIM,
        TERMINAL
    }

    public enum CandidateState {
        ELIGIBLE,
        SUPPRESSED
    }

    public enum SuppressionReason {
        NONE,
        EXECUTOR_UNAVAILABLE,
        ADOPTION_NOT_READY,
        DISCRETIONARY_CONTEXT_BLOCKED
    }

    public enum DecisionDisposition {
        EVALUATING,
        OPINION_DISABLED,
        FROZEN,
        MANDATORY_AUTHORITY,
        NO_CANDIDATES,
        BELOW_ACTIVATION_THRESHOLD,
        COMMITMENT_HOLD,
        SWITCH_MARGIN_HOLD,
        PENDING_INTENT_RETAINED,
        ADOPTED_INTENT_RETAINED,
        RUNNING_INTENT_RETAINED,
        /** Director invariant violated — see transition detail; intent was re-issued as repair. */
        DIRECTOR_INCONSISTENCY,
        INTENT_ISSUED
    }

    /** A score copied at evaluation time, or an explicit unscored suppression. */
    public record Candidate(
            DiscretionaryActivity activity,
            DiscretionaryCandidateKey candidateKey,
            ActivityUtilityBreakdown breakdown,
            CandidateState state,
            SuppressionReason suppressionReason,
            String suppressionDetail,
            ExecutionEvidence execution) {

        public Candidate {
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(candidateKey, "candidateKey");
            if (candidateKey.activity() != activity) {
                throw new IllegalArgumentException("candidate key activity does not match candidate");
            }
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(suppressionReason, "suppressionReason");
            suppressionDetail = suppressionDetail == null ? "" : suppressionDetail;
            if (breakdown != null && breakdown.activity() != activity) {
                throw new IllegalArgumentException("breakdown activity does not match candidate");
            }
            if (state == CandidateState.ELIGIBLE && breakdown == null) {
                throw new IllegalArgumentException("eligible candidate requires a score breakdown");
            }
            if (state == CandidateState.ELIGIBLE && suppressionReason != SuppressionReason.NONE) {
                throw new IllegalArgumentException("eligible candidate cannot have a suppression reason");
            }
            if (state == CandidateState.SUPPRESSED && suppressionReason == SuppressionReason.NONE) {
                throw new IllegalArgumentException("suppressed candidate requires a reason");
            }
        }

        public static Candidate eligible(ActivityUtilityBreakdown breakdown) {
            return eligible(breakdown, null);
        }

        /** Task 43 — why this candidate was legally in the comparison. */
        public static Candidate eligible(
                ActivityUtilityBreakdown breakdown, ExecutionEvidence execution) {
            return eligible(
                    DiscretionaryCandidateKey.singleton(breakdown.activity()),
                    breakdown,
                    execution);
        }

        public static Candidate eligible(
                DiscretionaryCandidateKey candidateKey,
                ActivityUtilityBreakdown breakdown,
                ExecutionEvidence execution) {
            Objects.requireNonNull(breakdown, "breakdown");
            return new Candidate(
                    breakdown.activity(), candidateKey, breakdown, CandidateState.ELIGIBLE,
                    SuppressionReason.NONE, "", execution);
        }

        public static Candidate suppressed(
                DiscretionaryActivity activity,
                ActivityUtilityBreakdown breakdown,
                SuppressionReason reason) {
            return suppressed(activity, breakdown, reason, "");
        }

        public static Candidate suppressed(
                DiscretionaryActivity activity,
                ActivityUtilityBreakdown breakdown,
                SuppressionReason reason,
                String suppressionDetail) {
            return suppressed(activity, breakdown, reason, suppressionDetail, null);
        }

        public static Candidate suppressed(
                DiscretionaryActivity activity,
                ActivityUtilityBreakdown breakdown,
                SuppressionReason reason,
                String suppressionDetail,
                ExecutionEvidence execution) {
            return suppressed(
                    DiscretionaryCandidateKey.singleton(activity), breakdown, reason,
                    suppressionDetail, execution);
        }

        public static Candidate suppressed(
                DiscretionaryCandidateKey candidateKey,
                ActivityUtilityBreakdown breakdown,
                SuppressionReason reason,
                String suppressionDetail,
                ExecutionEvidence execution) {
            return new Candidate(
                    candidateKey.activity(), candidateKey, breakdown, CandidateState.SUPPRESSED,
                    reason, suppressionDetail, execution);
        }
    }

    /** Exact lifecycle evidence appended to the decision that created the intent. */
    public record Transition(
            long gameTime,
            Stage stage,
            UUID intentId,
            DiscretionaryActivity activity,
            IntentLifecycle lifecycle,
            InvalidationCause invalidationCause,
            String detail) {

        public Transition {
            Objects.requireNonNull(stage, "stage");
            invalidationCause = invalidationCause == null ? InvalidationCause.NONE : invalidationCause;
            detail = detail == null ? "" : detail;
        }
    }

    /** Actual activity-memory changes captured at the terminal. */
    public record ActivityLearningDelta(
            float preference,
            float repetition,
            float recentReward,
            int recentFailures,
            long lastPerformed,
            long recentDuration) {

        public boolean changedAnything() {
            return preference != 0f
                    || repetition != 0f
                    || recentReward != 0f
                    || recentFailures != 0
                    || lastPerformed != 0L
                    || recentDuration != 0L;
        }
    }

    /** Actual learning-pipeline receipt captured at the terminal, never reconstructed later. */
    public record LearningOutcome(
            long gameTime,
            ActivityKind activity,
            ExperienceKind terminalKind,
            OutcomeClass outcome,
            ExperienceCause cause,
            boolean activityLearningEligible,
            ActivityLearningDelta activityDelta,
            Map<Long, Float> placePreferenceDeltas,
            Map<EnvironmentKind, Float> environmentPreferenceDeltas) {

        public LearningOutcome {
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(terminalKind, "terminalKind");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(activityDelta, "activityDelta");
            placePreferenceDeltas = Map.copyOf(placePreferenceDeltas);
            environmentPreferenceDeltas = Map.copyOf(environmentPreferenceDeltas);
        }

        public boolean changedAnything() {
            return activityDelta.changedAnything()
                    || !placePreferenceDeltas.isEmpty()
                    || !environmentPreferenceDeltas.isEmpty();
        }
    }

    /** Immutable snapshot safe for later readout projection. */
    public record Decision(
            long decisionId,
            long evaluatedAtGameTime,
            List<Candidate> candidates,
            DecisionDisposition disposition,
            InvalidationCause dispositionCause,
            DiscretionaryActivity selectedActivity,
            DiscretionaryCandidateKey selectedCandidateKey,
            UUID intentId,
            List<Transition> transitions,
            List<LearningOutcome> learningOutcomes,
            boolean closed) {

        public Decision {
            candidates = List.copyOf(candidates);
            Objects.requireNonNull(disposition, "disposition");
            dispositionCause = dispositionCause == null ? InvalidationCause.NONE : dispositionCause;
            transitions = List.copyOf(transitions);
            learningOutcomes = List.copyOf(learningOutcomes);
        }
    }

    private static final class MutableDecision {
        private final long decisionId;
        private final long evaluatedAtGameTime;
        private final List<Candidate> candidates;
        private final List<Transition> transitions = new ArrayList<>();
        private final List<LearningOutcome> learningOutcomes = new ArrayList<>();
        private DecisionDisposition disposition = DecisionDisposition.EVALUATING;
        private InvalidationCause dispositionCause = InvalidationCause.NONE;
        private DiscretionaryActivity selectedActivity;
        private DiscretionaryCandidateKey selectedCandidateKey;
        private UUID intentId;
        private boolean closed;

        private MutableDecision(long decisionId, long evaluatedAtGameTime, List<Candidate> candidates) {
            this.decisionId = decisionId;
            this.evaluatedAtGameTime = evaluatedAtGameTime;
            this.candidates = List.copyOf(candidates);
        }

        private Decision snapshot() {
            return new Decision(
                    decisionId,
                    evaluatedAtGameTime,
                    candidates,
                    disposition,
                    dispositionCause,
                    selectedActivity,
                    selectedCandidateKey,
                    intentId,
                    transitions,
                    learningOutcomes,
                    closed);
        }
    }

    private final Deque<MutableDecision> decisions = new ArrayDeque<>();
    private long nextDecisionId = 1L;

    /**
     * Task 43 — bounded history of yield transaction phases.
     *
     * <p>Sized to the decision history so a transaction and the decision that caused it stay
     * inspectable together; the request itself carries its causal ids, so correctness never depends
     * on the window.
     */
    private final java.util.ArrayDeque<YieldEvent> yieldEvents = new java.util.ArrayDeque<>();

    private static final int MAX_YIELD_EVENTS = 48;

    public void recordYieldEvent(YieldEvent event) {
        if (event == null) {
            return;
        }
        yieldEvents.addLast(event);
        while (yieldEvents.size() > MAX_YIELD_EVENTS) {
            yieldEvents.removeFirst();
        }
    }

    public List<YieldEvent> yieldEvents() {
        return List.copyOf(yieldEvents);
    }

    public java.util.Optional<YieldEvent> lastYieldEvent() {
        return java.util.Optional.ofNullable(yieldEvents.peekLast());
    }

    public long beginDecision(long gameTime, List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (decisions.size() >= DiscretionaryDirectorConstants.TRACE_DECISION_CAPACITY) {
            evictOneWholeDecision();
        }
        long decisionId = nextDecisionId++;
        if (nextDecisionId <= 0L) {
            nextDecisionId = 1L;
        }
        decisions.addLast(new MutableDecision(decisionId, gameTime, candidates));
        return decisionId;
    }

    public void conclude(
            long decisionId,
            DecisionDisposition disposition,
            DiscretionaryActivity selectedActivity,
            boolean closed) {
        conclude(decisionId, disposition, InvalidationCause.NONE, selectedActivity, closed);
    }

    public void conclude(
            long decisionId,
            DecisionDisposition disposition,
            InvalidationCause dispositionCause,
            DiscretionaryActivity selectedActivity,
            boolean closed) {
        MutableDecision decision = find(decisionId);
        if (decision == null) {
            return;
        }
        decision.disposition = Objects.requireNonNull(disposition, "disposition");
        decision.dispositionCause = dispositionCause == null
                ? InvalidationCause.NONE
                : dispositionCause;
        decision.selectedActivity = selectedActivity;
        decision.selectedCandidateKey = selectedActivity == null
                ? null
                : DiscretionaryCandidateKey.singleton(selectedActivity);
        decision.closed = closed;
    }

    /** Subject-aware conclusion; SOCIAL cannot be reconstructed from an activity enum. */
    public void concludeCandidate(
            long decisionId,
            DecisionDisposition disposition,
            InvalidationCause dispositionCause,
            DiscretionaryCandidateKey selectedCandidateKey,
            boolean closed) {
        MutableDecision decision = find(decisionId);
        if (decision == null) {
            return;
        }
        decision.disposition = Objects.requireNonNull(disposition, "disposition");
        decision.dispositionCause = dispositionCause == null
                ? InvalidationCause.NONE
                : dispositionCause;
        decision.selectedCandidateKey = selectedCandidateKey;
        decision.selectedActivity = selectedCandidateKey == null
                ? null
                : selectedCandidateKey.activity();
        decision.closed = closed;
    }

    public void attachIntent(long decisionId, UUID intentId) {
        MutableDecision decision = find(decisionId);
        if (decision != null) {
            decision.intentId = Objects.requireNonNull(intentId, "intentId");
        }
    }

    public void transition(
            long decisionId,
            long gameTime,
            Stage stage,
            UUID intentId,
            DiscretionaryActivity activity,
            IntentLifecycle lifecycle,
            InvalidationCause invalidationCause,
            String detail) {
        MutableDecision decision = find(decisionId);
        if (decision == null) {
            return;
        }
        decision.transitions.add(new Transition(
                gameTime, stage, intentId, activity, lifecycle, invalidationCause, detail));
        if (stage == Stage.TERMINAL) {
            decision.closed = true;
        }
    }

    public void recordLearning(long decisionId, LearningOutcome outcome) {
        MutableDecision decision = find(decisionId);
        if (decision != null) {
            decision.learningOutcomes.add(Objects.requireNonNull(outcome, "outcome"));
        }
    }

    public List<Decision> snapshot() {
        return decisions.stream().map(MutableDecision::snapshot).toList();
    }

    public void clear() {
        decisions.clear();
        // Task 43: the yield ring escaped the reset contract. Leaving it behind produced history
        // that outlived the decisions it referred to, while decision ids restarted from 1 - so an
        // ended transaction would appear to cite a decision that had not happened yet.
        yieldEvents.clear();
        nextDecisionId = 1L;
    }

    private MutableDecision find(long decisionId) {
        for (MutableDecision decision : decisions) {
            if (decision.decisionId == decisionId) {
                return decision;
            }
        }
        return null;
    }

    private void evictOneWholeDecision() {
        var iterator = decisions.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().closed) {
                iterator.remove();
                return;
            }
        }
        // The director can own at most one running and one pending intent. This defensive fallback
        // preserves the hard collection bound if that ownership invariant is ever violated, while
        // still removing a whole record rather than leaving a misleading partial chain.
        decisions.removeFirst();
    }
}
