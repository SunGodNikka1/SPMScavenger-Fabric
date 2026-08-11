package com.noobk.spmscavenger.experience;

import net.minecraft.core.BlockPos;
import com.noobk.spmscavenger.opinion.EnvironmentProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0b — immutable raw experience record emitted by existing terminals (GAO-0c routes it).
 */
public record ExperienceEvent(
        ExperienceKind kind,
        long gameTime,
        UUID episodeId,
        OutcomeClass outcome,
        ExperienceCause cause,
        float engagementDelta,
        float boredomDelta,
        float satisfactionDelta,
        float stressDelta,
        float noveltyDelta,
        Optional<ActivityKind> activity,
        Optional<BlockPos> place,
        Optional<UUID> entity,
        Optional<EnvironmentProfile> environment) {

    public ExperienceEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(episodeId, "episodeId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(environment, "environment");
        engagementDelta = requireFinite(engagementDelta, "engagementDelta");
        boredomDelta = requireFinite(boredomDelta, "boredomDelta");
        satisfactionDelta = requireFinite(satisfactionDelta, "satisfactionDelta");
        stressDelta = requireFinite(stressDelta, "stressDelta");
        noveltyDelta = requireFinite(noveltyDelta, "noveltyDelta");
    }

    public ExperienceEvent(
            ExperienceKind kind,
            long gameTime,
            UUID episodeId,
            OutcomeClass outcome,
            ExperienceCause cause,
            float engagementDelta,
            float boredomDelta,
            float satisfactionDelta,
            float stressDelta,
            float noveltyDelta,
            Optional<ActivityKind> activity,
            Optional<BlockPos> place,
            Optional<UUID> entity) {
        this(kind, gameTime, episodeId, outcome, cause, engagementDelta, boredomDelta,
                satisfactionDelta, stressDelta, noveltyDelta, activity, place, entity,
                Optional.empty());
    }

    private static float requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
