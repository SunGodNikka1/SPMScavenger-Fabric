package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;

/**
 * GAO-4 — determines when discretionary scoring/intent is allowed.
 */
public final class DiscretionaryEligibility {

    private DiscretionaryEligibility() {}

    public static boolean isDiscretionaryEligible(
            ActivityObservationService.Observation observation, boolean combatTarget) {
        if (combatTarget) {
            return false;
        }
        if (observation.unknownActive()) {
            return false;
        }
        for (ActivityClass activity : observation.activeClasses()) {
            if (blocksDiscretionaryChoice(activity)) {
                return false;
            }
        }
        return true;
    }

    public static InvalidationCause invalidationForObservation(
            ActivityObservationService.Observation observation, boolean combatTarget) {
        if (combatTarget) {
            return InvalidationCause.COMBAT_TARGET;
        }
        if (observation.unknownActive()) {
            return InvalidationCause.UNKNOWN_ACTIVE;
        }
        for (ActivityClass activity : observation.activeClasses()) {
            if (blocksDiscretionaryChoice(activity)) {
                return mandatoryCause(activity);
            }
        }
        return InvalidationCause.NONE;
    }

    private static boolean blocksDiscretionaryChoice(ActivityClass activity) {
        return switch (activity) {
            case MANDATORY_SAFETY,
                    SHELTER_HOLD,
                    MANDATORY_COMMAND,
                    MANDATORY_COMBAT,
                    MANDATORY_SURVIVAL,
                    PROJECT_EXECUTION,
                    PRODUCTIVE_COOP,
                    SCAVENGE_WORK,
                    SCAVENGE_LOOT,
                    FARMING,
                    DUNGEON_TRAIN,
                    SOCIAL_TRAVEL,
                    COMBAT_PREP,
                    SOCIAL_REFLEX -> true;
            default -> false;
        };
    }

    private static InvalidationCause mandatoryCause(ActivityClass activity) {
        return switch (activity) {
            case MANDATORY_COMMAND -> InvalidationCause.PLAYER_COMMAND;
            case MANDATORY_COMBAT -> InvalidationCause.COMBAT_TARGET;
            default -> InvalidationCause.MANDATORY_AUTHORITY;
        };
    }
}
