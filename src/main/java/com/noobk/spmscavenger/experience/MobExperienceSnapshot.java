package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.ActivityOpinionMemory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RET-GAO-1 — minimal per-mob state that intentionally survives temporary chunk unload.
 *
 * <p>Ephemeral execution state (live episodes, REST claims, director intents, tombstones) is
 * deliberately excluded. If this snapshot is evicted from the bounded frozen store, all contained
 * learned state is lost for that session (no disk persistence in Task 35).
 */
public record MobExperienceSnapshot(
        UUID mobId,
        float engagement,
        float boredom,
        float satisfaction,
        float stress,
        float novelty,
        int ticksSinceMeaningfulProgress,
        Map<ActivityKind, ActivityOpinionMemory.Snapshot> activityOpinions,
        Map<Long, Float> placePreferences,
        long parkedAtGameTime) {

    public MobExperienceSnapshot {
        Objects.requireNonNull(mobId, "mobId");
        Objects.requireNonNull(activityOpinions, "activityOpinions");
        Objects.requireNonNull(placePreferences, "placePreferences");
        activityOpinions = Map.copyOf(activityOpinions);
        placePreferences = Map.copyOf(placePreferences);
    }
}
