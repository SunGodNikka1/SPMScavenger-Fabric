package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — authoritative episode open/terminal boundary (D-GAO-023).
 *
 * <p>OPEN events bind an episode and may emit affect; only closing events commit terminal learning
 * and set {@link ActivityEpisode#closed}.
 */
public final class EpisodeBoundaryPolicy {

    private EpisodeBoundaryPolicy() {}

    public static boolean isTerminal(ExperienceEvent event) {
        return closesEpisode(event.kind(), event.cause());
    }

    public static boolean closesEpisode(ExperienceKind kind, ExperienceCause cause) {
        return switch (kind) {
            case REST_SESSION -> cause != ExperienceCause.REST_SESSION_OPEN;
            case EXPEDITION_UNLOCKED, EXPEDITION_STAGE, BLOCK_BROKEN, STAIR_STEP, ORE_ACQUIRED,
                    RESOURCE_HARVEST -> false;
            case EXPEDITION_END, PROJECT_END, VEIN_SESSION_END, CAVE_HANDOFF_ACCEPTED,
                    SOCIAL_EXPEDITION, SOCIAL_INTERACTION -> true;
        };
    }
}
