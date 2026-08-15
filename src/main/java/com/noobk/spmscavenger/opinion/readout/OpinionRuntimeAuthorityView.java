package com.noobk.spmscavenger.opinion.readout;

import java.util.List;
import java.util.Objects;

/**
 * VR-T1.5c — one-shot runtime authority diagnostics copied at inspector request time.
 *
 * <p>Read-only: no {@code canUse}, {@code canContinueToUse}, or host relationship probes.
 */
public record OpinionRuntimeAuthorityView(
        String latestDispositionCause,
        List<RunningGoalView> runningGoals,
        String discretionaryBlockerGoal,
        String discretionaryBlockerActivity,
        String discretionaryBlockerCause,
        boolean combatTarget,
        String socialAdmissionTargetId,
        String greetClaimTargetId,
        long greetClaimTicksRemaining,
        String socialBindingPhase,
        String socialBindingSubjectId) {

    public static final int MAX_RUNNING_GOALS = 16;
    /** Sentinel: no greet claim episode is open. */
    public static final long NO_GREET_CLAIM_TICKS = -1L;

    private static final OpinionRuntimeAuthorityView EMPTY = new OpinionRuntimeAuthorityView(
            "",
            List.of(),
            "",
            "",
            "",
            false,
            "",
            "",
            NO_GREET_CLAIM_TICKS,
            "",
            "");

    public OpinionRuntimeAuthorityView {
        latestDispositionCause = latestDispositionCause == null ? "" : latestDispositionCause;
        runningGoals = List.copyOf(runningGoals);
        discretionaryBlockerGoal = discretionaryBlockerGoal == null ? "" : discretionaryBlockerGoal;
        discretionaryBlockerActivity =
                discretionaryBlockerActivity == null ? "" : discretionaryBlockerActivity;
        discretionaryBlockerCause = discretionaryBlockerCause == null ? "" : discretionaryBlockerCause;
        socialAdmissionTargetId = socialAdmissionTargetId == null ? "" : socialAdmissionTargetId;
        greetClaimTargetId = greetClaimTargetId == null ? "" : greetClaimTargetId;
        socialBindingPhase = socialBindingPhase == null ? "" : socialBindingPhase;
        socialBindingSubjectId = socialBindingSubjectId == null ? "" : socialBindingSubjectId;
    }

    public static OpinionRuntimeAuthorityView empty() {
        return EMPTY;
    }

    public boolean hasContent() {
        return !latestDispositionCause.isBlank()
                || !runningGoals.isEmpty()
                || !discretionaryBlockerGoal.isBlank()
                || !discretionaryBlockerActivity.isBlank()
                || !discretionaryBlockerCause.isBlank()
                || combatTarget
                || !socialAdmissionTargetId.isBlank()
                || greetClaimTicksRemaining >= 0
                || !socialBindingPhase.isBlank()
                || !socialBindingSubjectId.isBlank();
    }
}
