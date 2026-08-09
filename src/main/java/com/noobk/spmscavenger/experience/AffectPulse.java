package com.noobk.spmscavenger.experience;

import java.util.UUID;

/**
 * GAO-0c — bounded immediate affect output from {@link ActivityEpisode}.
 */
public record AffectPulse(
        UUID episodeId,
        ExperienceKind kind,
        long gameTime,
        float engagementDelta,
        float boredomDelta,
        float satisfactionDelta,
        float stressDelta,
        float noveltyDelta) {
}
