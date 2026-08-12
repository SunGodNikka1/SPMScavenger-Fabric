package com.noobk.spmscavenger.opinion.readout;

import java.util.List;
import java.util.Objects;

/** Bounded causal decision row for inspector wire/UI. */
public record OpinionReadoutDecisionView(
        long decisionId,
        long evaluatedAtGameTime,
        String disposition,
        String dispositionCause,
        String selectedActivity,
        String explanation,
        List<String> candidateLines,
        List<String> transitionLines,
        boolean counterfactualOnly) {

    public static final int MAX_CANDIDATE_LINES = 6;
    public static final int MAX_TRANSITION_LINES = 8;

    public OpinionReadoutDecisionView {
        Objects.requireNonNull(disposition, "disposition");
        dispositionCause = dispositionCause == null ? "" : dispositionCause;
        selectedActivity = selectedActivity == null ? "" : selectedActivity;
        explanation = explanation == null ? "" : explanation;
        candidateLines = List.copyOf(candidateLines);
        transitionLines = List.copyOf(transitionLines);
    }
}
