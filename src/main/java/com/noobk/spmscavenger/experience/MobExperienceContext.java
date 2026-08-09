package com.noobk.spmscavenger.experience;

import com.noobk.spmscavenger.opinion.AffectiveState;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.OpinionMemory;
import com.noobk.spmscavenger.opinion.OpinionMemoryService;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c/GAO-1 — per-mob episode registry, REST claims, and short-term mood.
 */
public final class MobExperienceContext {

    private final UUID mobId;
    private final AffectiveState affectiveState = new AffectiveState();
    private final OpinionMemory opinionMemory = new OpinionMemory();
    private final OpinionExperienceSinks sinks;
    private final EpisodeRoutingPipeline pipeline;
    private final Map<UUID, ActivityEpisode> episodes = new HashMap<>();
    private final Map<ActivityKind, Integer> executionFailureTotals = new EnumMap<>(ActivityKind.class);
    private Optional<RestSessionClaim> restClaim = Optional.empty();
    private boolean frozen;

    public MobExperienceContext(UUID mobId, OpinionExperienceSinks delegate) {
        this.mobId = Objects.requireNonNull(mobId, "mobId");
        OpinionExperienceSinks external =
                delegate == null ? OpinionExperienceSinks.noOp() : delegate;
        this.sinks = new OpinionExperienceSinks() {
            @Override
            public void onAffectPulse(AffectPulse pulse) {
                if (OpinionFeatureGate.isEnabled() && !frozen) {
                    affectiveState.applyPulse(pulse);
                }
                external.onAffectPulse(pulse);
            }

            @Override
            public void onLearningEvidence(EpisodeLearningEvidence evidence) {
                OpinionMemoryService.apply(MobExperienceContext.this, evidence);
                external.onLearningEvidence(evidence);
            }
        };
        this.pipeline = new EpisodeRoutingPipeline(this, sinks);
    }

    public UUID mobId() {
        return mobId;
    }

    public AffectiveState affectiveState() {
        return affectiveState;
    }

    public OpinionMemory opinionMemory() {
        return opinionMemory;
    }

    public long episodeDuration(UUID episodeId, long closeTime) {
        ActivityEpisode episode = episodes.get(episodeId);
        if (episode == null) {
            return 0L;
        }
        return Math.max(0L, closeTime - episode.openedAtGameTime());
    }

    public EpisodeRoutingPipeline pipeline() {
        return pipeline;
    }

    public OpinionExperienceSinks sinks() {
        return sinks;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        frozen = true;
        affectiveState.freeze();
        invalidateEphemeral();
    }

    public void resume() {
        frozen = false;
        affectiveState.resume();
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
