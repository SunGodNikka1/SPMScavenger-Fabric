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
        ActivityEpisode episode = context.episodeFor(event.episodeId());
        if (episode.isSuspended() && event.outcome() == OutcomeClass.PROTECTED_INTERRUPT) {
            episode.resume();
        }
        episode.ingest(event, sinks, context);
    }
}
