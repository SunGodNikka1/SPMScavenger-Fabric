package com.noobk.spmscavenger.experience;

import java.util.Objects;

/**
 * GAO-0c — routes immutable {@link ExperienceEvent} records into {@link ActivityEpisode} owners.
 */
public final class EpisodeRoutingPipeline implements ExperiencePipeline {

    private final MobExperienceContext context;
    private final OpinionExperienceSinks sinks;

    EpisodeRoutingPipeline(MobExperienceContext context, OpinionExperienceSinks sinks) {
        this.context = Objects.requireNonNull(context, "context");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
    }

    @Override
    public void accept(ExperienceEvent event) {
        ActivityEpisode episode = context.ensureEpisode(
                event.episodeId(), event.gameTime(), event.activity());
        if (episode.isSuspended() && event.outcome() == OutcomeClass.PROTECTED_INTERRUPT) {
            episode.resume();
        }
        episode.ingest(event, sinks, context);
        // Gate RET-1b: terminal ownership owns memory lifetime. The event that ends an episode is
        // the one that releases it, so nothing accumulates between sweeps.
        if (episode.isClosed()) {
            context.compactEpisodeIfClosed(event.episodeId());
        }
    }
}
