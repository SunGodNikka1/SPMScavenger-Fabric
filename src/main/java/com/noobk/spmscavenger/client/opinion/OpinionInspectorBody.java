package com.noobk.spmscavenger.client.opinion;

import com.noobk.spmscavenger.opinion.readout.ActivityAdmissionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutDecisionView;
import com.noobk.spmscavenger.opinion.readout.OpinionReadoutSnapshot;

/**
 * Task 43 item 8 — Inspector body composition, extracted from the screen.
 *
 * <p>Pure string assembly over an already-projected snapshot: no Minecraft types, no derivation.
 * Extracted so the presentation layer has a regression of its own — the previous gap was precisely
 * that the server recorded a yield transaction, the codec shipped it, and the screen never rendered
 * it, which no server-side test could catch.
 */
final class OpinionInspectorBody {

    private OpinionInspectorBody() {
    }

    static java.util.List<String> compose(OpinionReadoutSnapshot snapshot) {
        java.util.List<String> bodyLines = new java.util.ArrayList<>();
        bodyLines.add("Status: " + snapshot.status());
        bodyLines.addAll(snapshot.summaryLines());
        bodyLines.add("");
        bodyLines.add("— Affect —");
        bodyLines.add("engagement=" + snapshot.engagement()
                + " boredom=" + snapshot.boredom()
                + " satisfaction=" + snapshot.satisfaction());
        bodyLines.add("stress=" + snapshot.stress()
                + " novelty=" + snapshot.novelty()
                + " frozen=" + snapshot.frozen()
                + " ticksSinceProgress=" + snapshot.ticksSinceMeaningfulProgress());
        bodyLines.add("");
        bodyLines.add("— Director layers —");
        if (!snapshot.incumbentActivity().isBlank()) {
            bodyLines.add("incumbent=" + snapshot.incumbentActivity());
        }
        if (!snapshot.currentIntentActivity().isBlank()) {
            bodyLines.add("intent=" + snapshot.currentIntentActivity()
                    + " (" + snapshot.currentIntentLifecycle() + ")");
        }
        bodyLines.add("latestDecisionDisposition=" + snapshot.currentDisposition()
                + " restPhase=" + snapshot.restAuthorityPhase());
        ActivityAdmissionView explore = snapshot.exploreAdmission();
        ActivityAdmissionView rest = snapshot.restAdmission();
        if (!explore.isEmpty()) {
            bodyLines.add("exploreAdmission: installed=" + explore.executorPresent()
                    + " adoptable=" + explore.adoptionReady()
                    + " blocker=" + explore.blocker()
                    + (explore.detail().isBlank() ? "" : " (" + explore.detail() + ")"));
        }
        if (!rest.isEmpty()) {
            bodyLines.add("restAdmission: installed=" + rest.executorPresent()
                    + " adoptable=" + rest.adoptionReady()
                    + " blocker=" + rest.blocker()
                    + (rest.detail().isBlank() ? "" : " (" + rest.detail() + ")"));
        }
        bodyLines.add("placePreferences=" + snapshot.placePreferenceCount()
                + " entityPreferences=" + snapshot.entityPreferenceCount());
        bodyLines.add("");
        bodyLines.add("— Personality —");
        var p = snapshot.personality();
        bodyLines.add("curiosity=" + p.curiosity()
                + " sociability=" + p.sociability()
                + " risk=" + p.riskTolerance());
        bodyLines.add("persistence=" + p.persistence()
                + " materialism=" + p.materialism()
                + " adventure=" + p.adventurousness());
        bodyLines.add("");
        bodyLines.add("resting=" + snapshot.resting());
        snapshot.shelterHold().ifPresent(hold -> bodyLines.add(
                "shelter=" + hold.phase() + " @ "
                        + hold.anchorX() + "," + hold.anchorY() + "," + hold.anchorZ()));
        if (!snapshot.activityPreferences().isEmpty()) {
            bodyLines.add("");
            bodyLines.add("— Activity preferences —");
            snapshot.activityPreferences().forEach((key, value) ->
                    bodyLines.add(key + "=" + value));
        }
        if (!snapshot.environmentPreferences().isEmpty()) {
            bodyLines.add("");
            bodyLines.add("— Environment preferences —");
            snapshot.environmentPreferences().forEach((key, value) ->
                    bodyLines.add(key + "=" + value));
        }
        bodyLines.add("");
        bodyLines.add("— Recent decisions —");
        for (OpinionReadoutDecisionView decision : snapshot.recentDecisions()) {
            bodyLines.add("#" + decision.decisionId()
                    + " " + decision.disposition()
                    + (decision.counterfactualOnly() ? " (non-causal)" : ""));
            bodyLines.add("  " + decision.explanation());
            decision.candidateLines().forEach(line -> bodyLines.add("  cand: " + line));
            decision.transitionLines().forEach(line -> bodyLines.add("  tx: " + line));
        }
        // Task 43 item 8 - the transaction the server already recorded and shipped. Rendered
        // straight from the projected snapshot strings: the screen derives nothing.
        if (!snapshot.recentYieldEvents().isEmpty()) {
            bodyLines.add("");
            bodyLines.add("— Recent yield transactions —");
            snapshot.recentYieldEvents().forEach(bodyLines::add);
        }
        return bodyLines;
    }
}
