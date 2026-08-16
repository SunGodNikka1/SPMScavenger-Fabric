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
    /** Opinion-owned finite SPM greeting with an exact live execution binding. */
    DISCRETIONARY_SOCIAL,
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
    /** Mandatory arrived nighttime shelter; affective rest is observed independently. */
    SHELTER_HOLD,
    /**
     * V2-F — a village trade attempt: approach, claim, transact.
     *
     * <p>A <b>known non-mining MOVE holder</b>. V2-C models gather and trade as two acquisition
     * routes to one {@code MaterialDemand}, but that is competition at the <i>consumer</i> level and
     * is not evidence that the trade goal participates in the active mining project — so this is
     * ordinary host work, not cooperative project work. See {@code MoveHolderClassifier}.
     */
    VILLAGE_TRADE,
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
