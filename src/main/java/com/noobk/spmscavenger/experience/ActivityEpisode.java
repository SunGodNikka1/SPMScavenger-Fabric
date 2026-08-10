package com.noobk.spmscavenger.experience;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — causal owner for a subjective activity span. Aggregates milestones, emits bounded
 * affect pulses immediately, and commits normalized learning evidence at terminals/windows.
 */
public final class ActivityEpisode {

    private final UUID episodeId;
    private Optional<ActivityKind> activity;
    private final long openedAtGameTime;
    private boolean suspended;
    private boolean closed;
    private final Map<ExperienceKind, Integer> milestoneCounts = new EnumMap<>(ExperienceKind.class);

    public ActivityEpisode(UUID episodeId, Optional<ActivityKind> activity, long openedAtGameTime) {
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.openedAtGameTime = openedAtGameTime;
    }

    public UUID episodeId() {
        return episodeId;
    }

    public Optional<ActivityKind> activity() {
        return activity;
    }

    public long openedAtGameTime() {
        return openedAtGameTime;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isClosed() {
        return closed;
    }

    public void bindActivity(ActivityKind kind) {
        if (activity.isEmpty()) {
            activity = Optional.of(kind);
        }
    }

    public void suspend() {
        suspended = true;
    }

    public void resume() {
        suspended = false;
    }

    public void ingest(ExperienceEvent event, OpinionExperienceSinks sinks, MobExperienceContext context) {
        if (!episodeId.equals(event.episodeId())) {
            throw new IllegalArgumentException("episode mismatch");
        }
        if (closed) {
            return;
        }
        event.activity().ifPresent(this::bindActivity);

        if (ExperienceOutcomePolicy.mayEmitAffect(event.outcome())) {
            sinks.onAffectPulse(new AffectPulse(
                    episodeId,
                    event.kind(),
                    event.gameTime(),
                    event.engagementDelta(),
                    event.boredomDelta(),
                    event.satisfactionDelta(),
                    event.stressDelta(),
                    event.noveltyDelta()));
        }

        if (EpisodeNormalizationPolicy.isMilestone(event.kind())) {
            int count = milestoneCounts.merge(event.kind(), 1, Integer::sum);
            float weight = EpisodeNormalizationPolicy.repetitionWeight(event.kind(), count);
            if (weight > 0.0f
                    && ExperienceOutcomePolicy.mayEmitPreferenceLearning(event.outcome())) {
                sinks.onLearningEvidence(new EpisodeLearningEvidence(
                        episodeId,
                        activity,
                        event.kind(),
                        event.outcome(),
                        event.cause(),
                        weight * ExperienceOutcomePolicy.preferenceSign(event.outcome(), event.cause()),
                        event.gameTime()));
            }
        }

        if (EpisodeBoundaryPolicy.isTerminal(event)) {
            commitTerminal(event, sinks, context);
        }
    }

    private void commitTerminal(
            ExperienceEvent event, OpinionExperienceSinks sinks, MobExperienceContext context) {
        int failureCount = 0;
        if (event.outcome() == OutcomeClass.EXECUTION_FAILURE) {
            failureCount = event.activity()
                    .map(context::registerExecutionFailure)
                    .orElse(0);
        }
        if (ExperienceOutcomePolicy.mayEmitPreferenceLearning(event.outcome())) {
            float sign = ExperienceOutcomePolicy.preferenceSign(event.outcome(), event.cause());
            if (sign != 0.0f) {
                sinks.onLearningEvidence(new EpisodeLearningEvidence(
                        episodeId,
                        activity,
                        event.kind(),
                        event.outcome(),
                        event.cause(),
                        sign,
                        event.gameTime()));
            }
        } else if (ExperienceOutcomePolicy.mayEmitFailureLearning(
                event.outcome(), failureCount)) {
            sinks.onLearningEvidence(new EpisodeLearningEvidence(
                    episodeId,
                    activity,
                    event.kind(),
                    event.outcome(),
                    event.cause(),
                    -0.5f,
                    event.gameTime()));
        }
        closed = true;
    }

    int milestoneCount(ExperienceKind kind) {
        return milestoneCounts.getOrDefault(kind, 0);
    }
}
