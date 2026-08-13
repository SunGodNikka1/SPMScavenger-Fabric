package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.goal.ShelterNightAuthority;
import com.noobk.spmscavenger.opinion.ActivityOpinionMemory;
import com.noobk.spmscavenger.opinion.ActivityAdoptionBlocker;
import com.noobk.spmscavenger.opinion.DiscretionaryActivity;
import com.noobk.spmscavenger.opinion.DiscretionaryDirectorConstants;
import com.noobk.spmscavenger.opinion.DiscretionaryIntent;
import com.noobk.spmscavenger.opinion.EnvironmentKind;
import com.noobk.spmscavenger.opinion.ActivityAdmission;
import com.noobk.spmscavenger.opinion.ActivityAdmissions;
import com.noobk.spmscavenger.opinion.ExecutionEvidence;
import com.noobk.spmscavenger.opinion.YieldEvent;
import com.noobk.spmscavenger.opinion.OpinionDecisionTrace;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.PersonalityModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-8B Task 42B — plain-language projection over Task 42A evidence (D-GAO-041, PD-GAO-15).
 */
public final class OpinionReadoutExplanation {

    private OpinionReadoutExplanation() {
    }

    public static List<String> buildSummary(
            MobExperienceContext context,
            Optional<ShelterNightAuthority.Hold> shelterHold,
            Optional<OpinionDecisionTrace.Decision> latestDecision) {
        List<String> lines = new ArrayList<>();
        if (shelterHold.isPresent()) {
            ShelterNightAuthority.Hold hold = shelterHold.get();
            lines.add(shelterDoingLine(hold.phase()));
            lines.add("Because: Dusk shelter authority is active — not a discretionary mood choice");
            lines.add("Held by: SHELTER_HOLD / " + hold.phase());
            lines.add("Resting: "
                    + (context.hasLiveRestClaim() ? "yes (independent affective claim)" : "no"));
            appendDirectorLayers(lines, context);
            latestDecision.ifPresent(decision -> appendMandatorySuppression(lines, decision));
            return List.copyOf(lines);
        }

        latestDecision.ifPresentOrElse(
                decision -> appendOpinionChoiceSummary(lines, context, decision),
                () -> lines.add("Desired activity: none recorded"));
        appendDirectorLayers(lines, context);
        appendExecutionState(lines, context);
        appendAdmissions(lines, context);
        lines.add("Resting: " + (context.hasLiveRestClaim() ? "yes (affective rest claim is live)" : "no"));
        return List.copyOf(lines);
    }

    /**
     * Counterfactual evaluations are recorded at decision time — never inferred from current shelter
     * or other live authority that could rewrite older causal history (D-GAO-041 / D-GAO-044).
     */
    public static boolean isCounterfactualEvaluation(OpinionDecisionTrace.Decision decision) {
        return decision.disposition() == OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY;
    }

    public static OpinionReadoutDecisionView projectDecision(OpinionDecisionTrace.Decision decision) {
        boolean counterfactualOnly = isCounterfactualEvaluation(decision);
        List<String> candidates = new ArrayList<>();
        for (OpinionDecisionTrace.Candidate candidate : decision.candidates()) {
            if (candidates.size() >= OpinionReadoutDecisionView.MAX_CANDIDATE_LINES) {
                break;
            }
            if (candidate.state() == OpinionDecisionTrace.CandidateState.ELIGIBLE
                    && candidate.breakdown() != null) {
                var b = candidate.breakdown();
                candidates.add(candidate.activity().name()
                        + " total=" + format(b.total())
                        + " pref=" + format(b.preference())
                        + " boredom=" + format(b.boredomFit())
                        + " stress=" + format(b.stressFit())
                        + executionSuffix(candidate.execution()));
            } else {
                String detail = candidate.suppressionDetail();
                candidates.add(candidate.activity().name()
                        + " suppressed (" + candidate.suppressionReason()
                        + (detail.isBlank() ? "" : " — " + detail)
                        + ")"
                        + executionSuffix(candidate.execution()));
            }
        }

        List<String> yields = new ArrayList<>();

        List<String> transitions = new ArrayList<>();
        for (OpinionDecisionTrace.Transition transition : decision.transitions()) {
            if (transitions.size() >= OpinionReadoutDecisionView.MAX_TRANSITION_LINES) {
                break;
            }
            transitions.add(transition.stage()
                    + " "
                    + transition.lifecycle()
                    + (transition.detail().isBlank() ? "" : " — " + transition.detail()));
        }

        String selected = decision.selectedActivity() == null
                ? ""
                : decision.selectedActivity().name();
        return new OpinionReadoutDecisionView(
                decision.decisionId(),
                decision.evaluatedAtGameTime(),
                decision.disposition().name(),
                decision.dispositionCause().name(),
                selected,
                explainDisposition(decision),
                candidates,
                transitions,
                counterfactualOnly);
    }

    private static void appendDirectorLayers(List<String> lines, MobExperienceContext context) {
        context.discretionaryDirector().incumbentActivity().ifPresent(activity ->
                lines.add("Director incumbent: " + activity.name()));
        context.discretionaryDirector().pendingIntent().ifPresent(intent ->
                lines.add("Director pending: " + intent.activity().name()
                        + " (" + intent.lifecycle() + ")"));
        context.discretionaryDirector().runningIntent().ifPresent(intent ->
                lines.add("Director running: " + intent.activity().name()
                        + " (" + intent.lifecycle() + ")"));
    }

    private static String shelterDoingLine(ShelterNightAuthority.Phase phase) {
        return switch (phase) {
            case APPROACHING -> "Doing: Seeking night shelter (mandatory)";
            case SETTLED -> "Doing: Holding night shelter (mandatory)";
            case RETURNING -> "Doing: Returning to night shelter (mandatory)";
        };
    }

    private static void appendMandatorySuppression(
            List<String> lines, OpinionDecisionTrace.Decision decision) {
        if (decision.disposition() != OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY) {
            return;
        }
        decision.candidates().stream()
                .filter(c -> c.state() == OpinionDecisionTrace.CandidateState.ELIGIBLE)
                .max(Comparator.comparing(c -> c.breakdown().total()))
                .ifPresent(winner -> lines.add(
                        "Suppressed (non-causal): "
                                + winner.activity().name()
                                + " would have ranked highest but mandatory shelter blocks discretionary work"));
    }

    private static void appendOpinionChoiceSummary(
            List<String> lines,
            MobExperienceContext context,
            OpinionDecisionTrace.Decision decision) {
        if (isCounterfactualEvaluation(decision)) {
            lines.add("Desired activity: blocked by mandatory authority");
            lines.add("Because: " + explainDisposition(decision));
            appendMandatorySuppression(lines, decision);
            return;
        }

        String desired = decision.selectedActivity() == null
                ? "none"
                : decision.selectedActivity().name();
        lines.add("Desired activity: " + desired);
        lines.add("Because: " + explainDisposition(decision));
        appendSelectionRationale(lines, decision);
        decision.transitions().stream()
                .filter(t -> t.stage() == OpinionDecisionTrace.Stage.TERMINAL)
                .reduce((first, second) -> second)
                .ifPresent(terminal -> lines.add(
                        "Last outcome: " + terminal.lifecycle() + " — " + terminal.detail()));
    }

    private static void appendSelectionRationale(
            List<String> lines, OpinionDecisionTrace.Decision decision) {
        for (OpinionDecisionTrace.Candidate candidate : decision.candidates()) {
            if (candidate.state() != OpinionDecisionTrace.CandidateState.SUPPRESSED) {
                continue;
            }
            String detail = candidate.suppressionDetail();
            lines.add(candidate.activity().name() + " blocked: " + candidate.suppressionReason()
                    + (detail.isBlank() ? "" : " — " + detail));
        }
        decision.candidates().stream()
                .filter(candidate -> candidate.state() == OpinionDecisionTrace.CandidateState.ELIGIBLE
                        && candidate.activity() == decision.selectedActivity()
                        && candidate.breakdown() != null)
                .findFirst()
                .ifPresent(winner -> lines.add(
                        "Selected utility: " + format(winner.breakdown().total())));
    }

    private static void appendExecutionState(List<String> lines, MobExperienceContext context) {
        var director = context.discretionaryDirector();
        if (director.incumbentActivity().isPresent()) {
            lines.add("Execution: " + director.incumbentActivity().get().name()
                    + " is the adopted incumbent");
            return;
        }
        Optional<DiscretionaryIntent> pending = director.pendingIntent();
        if (pending.isPresent()) {
            DiscretionaryIntent intent = pending.get();
            long ttlRemaining = Math.max(
                    0L,
                    DiscretionaryDirectorConstants.PENDING_INTENT_TTL_TICKS
                            - (director.lastGameTime() - intent.issuedAtTick()));
            lines.add("Execution: no executor has adopted " + intent.activity().name() + " yet");
            lines.add("Pending TTL: ~" + ttlRemaining + " ticks remaining");
            return;
        }
        if (director.runningIntent().isPresent()) {
            DiscretionaryIntent running = director.runningIntent().get();
            lines.add("Execution: " + running.activity().name()
                    + " adopted (" + running.lifecycle() + ") but no incumbent recorded yet");
            return;
        }
        lines.add("Execution: no discretionary activity adopted yet");
    }

    private static void appendAdmissions(List<String> lines, MobExperienceContext context) {
        ActivityAdmissions admissions = context.discretionaryDirector().lastAdmissions();
        appendAdmissionLine(lines, DiscretionaryActivity.EXPLORE, admissions.explore());
        appendAdmissionLine(lines, DiscretionaryActivity.REST, admissions.rest());
    }

    private static void appendAdmissionLine(
            List<String> lines, DiscretionaryActivity activity, ActivityAdmission admission) {
        if (!admission.executorPresent() && admission.blocker() == ActivityAdoptionBlocker.EXECUTOR_DISABLED) {
            lines.add(activity.name() + " admission: executor not installed");
            return;
        }
        lines.add(activity.name() + " admission: installed="
                + admission.executorPresent()
                + " adoptable=" + admission.adoptionReady()
                + " blocker=" + admission.blocker().name()
                + (admission.detail().isBlank() ? "" : " (" + admission.detail() + ")"));
    }

    private static String explainDisposition(OpinionDecisionTrace.Decision decision) {
        if (isCounterfactualEvaluation(decision)) {
            return "Mandatory authority blocked discretionary scheduling — scores are diagnostic only";
        }
        return switch (decision.disposition()) {
            case INTENT_ISSUED -> "Highest utility candidate issued to the director as a new pending intent";
            case PENDING_INTENT_RETAINED -> "Pending intent retained — waiting for executor adoption";
            case ADOPTED_INTENT_RETAINED -> "Adopted intent retained — executor delivery in progress";
            case RUNNING_INTENT_RETAINED -> "Running intent retained — incumbent activity continues";
            case DIRECTOR_INCONSISTENCY ->
                    "Director invariant violated — pending intent re-issued; see transition detail";
            case MANDATORY_AUTHORITY -> "Mandatory authority blocked discretionary scheduling";
            case COMMITMENT_HOLD -> "Commitment hold prevented a switch";
            case SWITCH_MARGIN_HOLD -> "Switch margin not met";
            case BELOW_ACTIVATION_THRESHOLD -> "No candidate cleared activation threshold";
            case NO_CANDIDATES -> "No executor-backed candidates were eligible";
            case OPINION_DISABLED -> "Opinion feature gate is off";
            case FROZEN -> "Mob opinion state is frozen";
            case EVALUATING -> "Evaluation still open";
        };
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public static Map<String, Float> boundedActivityPreferences(
            Map<ActivityKind, ActivityOpinionMemory.Snapshot> snapshot) {
        Map<String, Float> out = new LinkedHashMap<>();
        snapshot.entrySet().stream()
                .sorted(Map.Entry.<ActivityKind, ActivityOpinionMemory.Snapshot>comparingByValue(
                        Comparator.comparing(ActivityOpinionMemory.Snapshot::preference)).reversed())
                .limit(OpinionReadoutSnapshot.MAX_ACTIVITY_PREFS)
                .forEach(entry -> out.put(entry.getKey().name(), entry.getValue().preference()));
        return Map.copyOf(out);
    }

    public static Map<String, Float> boundedEnvironmentPreferences(Map<EnvironmentKind, Float> snapshot) {
        Map<String, Float> out = new LinkedHashMap<>();
        snapshot.entrySet().stream()
                .sorted(Map.Entry.<EnvironmentKind, Float>comparingByValue().reversed())
                .limit(OpinionReadoutSnapshot.MAX_ENVIRONMENT_PREFS)
                .forEach(entry -> out.put(entry.getKey().name(), entry.getValue()));
        return Map.copyOf(out);
    }

    public static Optional<OpinionDecisionTrace.Decision> latestDecision(MobExperienceContext context) {
        List<OpinionDecisionTrace.Decision> decisions = context.discretionaryDirector().trace().snapshot();
        if (decisions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(decisions.getLast());
    }

    public static boolean opinionEnabled() {
        return OpinionFeatureGate.isEnabled();
    }

    public static PersonalityModel personalityOf(MobExperienceContext context) {
        return context.personalityModel();
    }

    public static Optional<OpinionShelterHoldView> shelterView(UUID mobId) {
        return ShelterNightAuthority.hold(mobId).map(hold -> new OpinionShelterHoldView(
                hold.phase().name(),
                hold.anchor().getX(),
                hold.anchor().getY(),
                hold.anchor().getZ(),
                hold.commitmentId().toString()));
    }

    public static List<OpinionReadoutDecisionView> recentDecisions(MobExperienceContext context) {
        List<OpinionDecisionTrace.Decision> all = context.discretionaryDirector().trace().snapshot();
        int start = Math.max(0, all.size() - OpinionReadoutSnapshot.MAX_DECISIONS);
        List<OpinionReadoutDecisionView> out = new ArrayList<>();
        for (int i = start; i < all.size(); i++) {
            out.add(projectDecision(all.get(i)));
        }
        return List.copyOf(out);
    }

    public static String currentDisposition(MobExperienceContext context) {
        return latestDecision(context)
                .map(d -> d.disposition().name())
                .orElse("");
    }

    /**
     * Task 43 item 8 — the copied decision evidence, not current executor state.
     *
     * <p>Without this the Inspector could show {@code RUNNING_INTENT_RETAINED} while giving no way
     * to see that the retained activity was <em>not adoptable</em> — the one thing that makes the
     * outcome surprising and the one thing D-GAO-050 exists to justify.
     */
    private static String executionSuffix(ExecutionEvidence evidence) {
        if (evidence == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        out.append(" [exec=").append(evidence.executorPresent() ? "yes" : "no");
        out.append(" adopt=").append(evidence.adoptionReady() ? "ready" : "blocked");
        if (!evidence.adoptionReady()) {
            out.append(':').append(evidence.adoptionBlocker());
        }
        if (evidence.runningIncumbent()) {
            out.append(" incumbent=yes cont=")
                    .append(evidence.continuable() ? "valid" : "invalid");
            if (!evidence.continuable()) {
                out.append(':').append(evidence.continuationBlocker());
            }
        }
        if (evidence.retainedByContinuation()) {
            out.append(" retainedByContinuation");
        }
        return out.append(']').toString();
    }

    /**
     * Task 43 item 8 — bounded typed yield history, sourced from {@code trace.yieldEvents()}.
     *
     * <p>Deliberately not {@code lastYieldOutcome}: that is convenience state describing only the
     * most recent ending, and cannot show a request that is still open or a transaction that ended
     * two decisions ago.
     */
    public static List<String> projectYieldEvents(List<YieldEvent> events, int max) {
        List<String> lines = new ArrayList<>();
        if (events == null) {
            return lines;
        }
        int from = Math.max(0, events.size() - max);
        for (YieldEvent event : events.subList(from, events.size())) {
            if (event.phase() == YieldEvent.Phase.REQUESTED) {
                lines.add("REQUESTED @" + event.requestedAt()
                        + " " + event.incumbentActivity() + " → " + event.challengerActivity()
                        + " origin=#" + event.originDecisionId()
                        + " expires=" + event.expiresAt());
            } else {
                lines.add("ENDED @" + event.gameTime()
                        + " " + event.incumbentActivity() + " → " + event.challengerActivity()
                        + " outcome=" + event.outcome()
                        + " origin=#" + event.originDecisionId()
                        + " after=" + event.durationTicks() + "t");
            }
        }
        return lines;
    }
}
