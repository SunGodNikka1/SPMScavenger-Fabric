package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.opinion.PersonalityModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GAO-8B Task 42B — immutable bounded server snapshot copied from existing live state only.
 *
 * <p>No client types; safe on the common classpath and over play networking.
 */
public record OpinionReadoutSnapshot(
        long requestId,
        int entityId,
        String mobDisplayName,
        OpinionReadoutStatus status,
        List<String> summaryLines,
        float engagement,
        float boredom,
        float satisfaction,
        float stress,
        float novelty,
        int ticksSinceMeaningfulProgress,
        boolean frozen,
        PersonalityModel personality,
        Map<String, Float> activityPreferences,
        Map<String, Float> environmentPreferences,
        int placePreferenceCount,
        int entityPreferenceCount,
        boolean resting,
        Optional<OpinionShelterHoldView> shelterHold,
        String incumbentActivity,
        String currentIntentActivity,
        String currentIntentLifecycle,
        String restAuthorityPhase,
        String currentDisposition,
        ActivityAdmissionView exploreAdmission,
        ActivityAdmissionView restAdmission,
        List<OpinionReadoutDecisionView> recentDecisions) {

    public static final int MAX_SUMMARY_LINES = 20;
    public static final int MAX_ACTIVITY_PREFS = 16;
    public static final int MAX_ENVIRONMENT_PREFS = 8;
    public static final int MAX_DECISIONS = 8;

    public OpinionReadoutSnapshot {
        Objects.requireNonNull(mobDisplayName, "mobDisplayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(personality, "personality");
        summaryLines = List.copyOf(summaryLines);
        activityPreferences = Map.copyOf(activityPreferences);
        environmentPreferences = Map.copyOf(environmentPreferences);
        Objects.requireNonNull(shelterHold, "shelterHold");
        incumbentActivity = incumbentActivity == null ? "" : incumbentActivity;
        currentIntentActivity = currentIntentActivity == null ? "" : currentIntentActivity;
        currentIntentLifecycle = currentIntentLifecycle == null ? "" : currentIntentLifecycle;
        restAuthorityPhase = restAuthorityPhase == null ? "" : restAuthorityPhase;
        currentDisposition = currentDisposition == null ? "" : currentDisposition;
        exploreAdmission = exploreAdmission == null
                ? ActivityAdmissionView.empty()
                : exploreAdmission;
        restAdmission = restAdmission == null ? ActivityAdmissionView.empty() : restAdmission;
        recentDecisions = List.copyOf(recentDecisions);
    }
}
