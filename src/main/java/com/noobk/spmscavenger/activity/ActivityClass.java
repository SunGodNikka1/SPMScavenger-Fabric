package com.noobk.spmscavenger.activity;

/**
 * GAO-0 — loader-independent semantic activity reported by a running Minecraft Goal.
 *
 * <p>This is observation only. A class grants no scheduler authority, changes no priority, and
 * selects no activity. Future Opinion phases may consume the richer taxonomy, while GAO-0 keeps
 * expedition readiness on its historical predicate.
 */
public enum ActivityClass {
    PASSIVE_HELPER,
    MANDATORY_SAFETY,
    MANDATORY_COMMAND,
    MANDATORY_SURVIVAL,
    SOCIAL_REFLEX,
    MANDATORY_COMBAT,
    SOCIAL_TRAVEL,
    COMBAT_PREP,
    SCAVENGE_LOOT,
    FARMING,
    DUNGEON_TRAIN,
    IDLE_CANDIDATE,
    PASSIVE_COSMETIC,
    PRODUCTIVE_COOP,
    SCAVENGE_WORK,
    PROJECT_EXECUTION,
    MAINTENANCE,
    REST_APPROACH,
    REST,
    EXPEDITION,
    PASSIVE_OBSERVER,
    UNKNOWN_ACTIVE;

    /** Exact GAO-0 compatibility list inherited from ExplorationActivityGoal. */
    public boolean isLegacyIdleNoise() {
        return this == IDLE_CANDIDATE
                || this == PASSIVE_COSMETIC
                || this == PASSIVE_OBSERVER;
    }

    public boolean isRest() {
        return this == REST;
    }

    public boolean isExpedition() {
        return this == EXPEDITION;
    }

    /** Scheduler occupancy is descriptive only; it is not permission to preempt the Goal. */
    public boolean isSchedulerOccupant() {
        return this != PASSIVE_COSMETIC && this != PASSIVE_OBSERVER;
    }
}
