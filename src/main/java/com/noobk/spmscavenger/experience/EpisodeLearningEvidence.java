package com.noobk.spmscavenger.experience;

import java.util.Optional;
import java.util.UUID;

/**
 * GAO-0c — normalized episode evidence destined for long-term {@code OpinionMemory} (GAO-2).
 */
public record EpisodeLearningEvidence(
        UUID episodeId,
        Optional<ActivityKind> activity,
        ExperienceKind terminalKind,
        OutcomeClass outcome,
        ExperienceCause cause,
        float repetitionWeight,
        long gameTime) {
}
