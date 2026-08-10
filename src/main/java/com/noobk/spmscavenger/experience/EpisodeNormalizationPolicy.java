package com.noobk.spmscavenger.experience;

/**
 * GAO-0c — converts high-frequency milestones into bounded learning weights (D-GAO-022).
 */
public final class EpisodeNormalizationPolicy {

    public static final int BLOCK_BROKEN_LEARNING_INTERVAL = 8;

    private EpisodeNormalizationPolicy() {
    }

    /**
     * @return normalized repetition weight when a milestone count crosses a learning window
     */
    public static float repetitionWeight(ExperienceKind kind, int milestoneCount) {
        return switch (kind) {
            case BLOCK_BROKEN -> milestoneCount > 0
                    && milestoneCount % BLOCK_BROKEN_LEARNING_INTERVAL == 0
                    ? 1.0f / BLOCK_BROKEN_LEARNING_INTERVAL
                    : 0.0f;
            case STAIR_STEP, EXPEDITION_STAGE, RESOURCE_HARVEST, ORE_ACQUIRED -> 1.0f;
            default -> 0.0f;
        };
    }

    public static boolean isMilestone(ExperienceKind kind) {
        return switch (kind) {
            case BLOCK_BROKEN, STAIR_STEP, EXPEDITION_STAGE, RESOURCE_HARVEST, ORE_ACQUIRED -> true;
            default -> false;
        };
    }

    public static boolean isTerminal(ExperienceKind kind) {
        return switch (kind) {
            case PROJECT_END, VEIN_SESSION_END, EXPEDITION_END, CAVE_HANDOFF_ACCEPTED,
                    SOCIAL_EXPEDITION, SOCIAL_INTERACTION -> true;
            default -> false;
        };
    }
}
