package com.noobk.spmscavenger.experience;

/**
 * GAO-0b — coarse milestone category for a raw {@link ExperienceEvent}.
 *
 * <p>Distinct from {@link com.noobk.spmscavenger.activity.ActivityClass}, which describes scheduler
 * observation rather than subjective activity identity.
 */
public enum ExperienceKind {
    BLOCK_BROKEN,
    STAIR_STEP,
    PROJECT_END,
    CAVE_HANDOFF_ACCEPTED,
    ORE_ACQUIRED,
    VEIN_SESSION_END,
    EXPEDITION_UNLOCKED,
    EXPEDITION_END,
    EXPEDITION_STAGE,
    SOCIAL_EXPEDITION,
    RESOURCE_HARVEST,
    REST_SESSION,
    SOCIAL_INTERACTION
}
