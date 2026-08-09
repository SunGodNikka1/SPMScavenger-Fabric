package com.noobk.spmscavenger.experience;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — per-mob episode registry and REST claim holder.
 */
public final class MobExperienceContext {

    private final UUID mobId;
    private final OpinionExperienceSinks sinks;
    private final EpisodeRoutingPipeline pipeline;
    private final Map<UUID, ActivityEpisode> episodes = new HashMap<>();
    private final Map<ActivityKind, Integer> executionFailureTotals = new EnumMap<>(ActivityKind.class);
    private Optional<RestSessionClaim> restClaim = Optional.empty();

    public MobExperienceContext(UUID mobId, OpinionExperienceSinks sinks) {
        this.mobId = Objects.requireNonNull(mobId, "mobId");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.pipeline = new EpisodeRoutingPipeline(this, sinks);
    }

    public UUID mobId() {
        return mobId;
    }

    public EpisodeRoutingPipeline pipeline() {
        return pipeline;
    }

    public OpinionExperienceSinks sinks() {
        return sinks;
    }

    public Optional<RestSessionClaim> restClaim() {
        return restClaim;
    }

    public boolean hasLiveRestClaim() {
        return restClaim.filter(RestSessionClaim::isLive).isPresent();
    }

    public void setRestClaim(Optional<RestSessionClaim> claim) {
        restClaim = Objects.requireNonNull(claim, "claim");
    }

    public ActivityEpisode openEpisode(Optional<ActivityKind> activity, long gameTime) {
        UUID episodeId = UUID.randomUUID();
        ActivityEpisode episode = new ActivityEpisode(episodeId, activity, gameTime);
        episodes.put(episodeId, episode);
        return episode;
    }

    public ActivityEpisode episodeFor(UUID episodeId) {
        return episodes.computeIfAbsent(episodeId, id -> new ActivityEpisode(id, Optional.empty(), 0L));
    }

    public int registerExecutionFailure(ActivityKind kind) {
        return executionFailureTotals.merge(kind, 1, Integer::sum);
    }

    public void invalidateEphemeral() {
        restClaim = Optional.empty();
        for (ActivityEpisode episode : episodes.values()) {
            if (!episode.isClosed()) {
                episode.suspend();
            }
        }
    }
}
