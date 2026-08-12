package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.goal.ShelterNightAuthority;
import com.noobk.spmscavenger.opinion.ActivityOpinionMemory;
import com.noobk.spmscavenger.opinion.DiscretionaryActivity;
import com.noobk.spmscavenger.opinion.EnvironmentKind;
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
                decision -> appendDecisionSummary(lines, context, decision),
                () -> lines.add("Doing: No recent discretionary evaluation recorded"));
        if (context.hasLiveRestClaim()) {
            lines.add("Resting: yes (affective rest claim is live)");
        }
        appendDirectorLayers(lines, context);
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
                        + " stress=" + format(b.stressFit()));
            } else {
                candidates.add(candidate.activity().name()
                        + " suppressed (" + candidate.suppressionReason() + ")");
            }
        }

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
        context.discretionaryDirector().intent().ifPresent(intent ->
                lines.add("Director intent: " + intent.activity().name()
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

    private static void appendDecisionSummary(
            List<String> lines,
            MobExperienceContext context,
            OpinionDecisionTrace.Decision decision) {
        lines.add("Doing: " + describeDoing(decision));
        lines.add("Because: " + explainDisposition(decision));
        appendDirectorLayers(lines, context);
        decision.transitions().stream()
                .filter(t -> t.stage() == OpinionDecisionTrace.Stage.TERMINAL)
                .reduce((first, second) -> second)
                .ifPresent(terminal -> lines.add(
                        "Last outcome: " + terminal.lifecycle() + " — " + terminal.detail()));
    }

    private static String describeDoing(OpinionDecisionTrace.Decision decision) {
        return switch (decision.disposition()) {
            case INTENT_ISSUED, EXISTING_INTENT_RETAINED -> decision.selectedActivity() == null
                    ? "Issued discretionary intent"
                    : "Pursuing " + decision.selectedActivity().name();
            case MANDATORY_AUTHORITY -> "Held by mandatory authority";
            case COMMITMENT_HOLD, SWITCH_MARGIN_HOLD -> "Holding current commitment";
            case BELOW_ACTIVATION_THRESHOLD -> "No activity above activation threshold";
            case NO_CANDIDATES -> "No eligible discretionary candidates";
            case OPINION_DISABLED -> "Opinion disabled";
            case FROZEN -> "Opinion frozen";
            case EVALUATING -> "Evaluating";
        };
    }

    private static String explainDisposition(OpinionDecisionTrace.Decision decision) {
        if (isCounterfactualEvaluation(decision)) {
            return "Mandatory authority blocked discretionary scheduling — scores are diagnostic only";
        }
        return switch (decision.disposition()) {
            case INTENT_ISSUED -> "Highest utility candidate issued to the director";
            case EXISTING_INTENT_RETAINED -> "Incumbent intent retained";
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
}
