package com.noobk.spmscavenger.experience;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * GAO-6R — deterministic social sub-episode ids that do not alias parent activity episodes.
 *
 * <p>Companion invites emit terminal {@link ExperienceKind#SOCIAL_EXPEDITION} on a dedicated episode
 * so an in-flight {@link ActivityKind#OVERLAND_EXPLORATION} span stays open until
 * {@link ExperienceKind#EXPEDITION_END}.
 */
public final class SocialExperienceEpisodes {

    private SocialExperienceEpisodes() {
    }

    /**
     * Stable per (expedition, companion) pair for the lifetime of that expedition identity.
     *
     * <p>Duplicate invite callbacks for the same companion during one expedition resolve to the same
     * id, so a completed social sub-episode tombstone makes re-emission idempotent.
     */
    public static UUID companionInviteEpisodeId(UUID expeditionEpisodeId, UUID companionId) {
        return UUID.nameUUIDFromBytes(
                (expeditionEpisodeId + "|" + companionId + "|" + ExperienceCause.SOCIAL_COMPANION_INVITE)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
