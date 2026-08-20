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

    /** Read-only inspector attribution — same predicate as discretionary eligibility. */
    public static boolean blocksDiscretionaryChoice(ActivityClass activity) {
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
                    SOCIAL_REFLEX,
                    // D-VR-082-A1 item 2: VILLAGE_TRADE is deliberate-band mandatory work at
                    // priority 3; the shared authority must not read a running trade as
                    // "discretionary eligible". This is the semantic lie the flags happened to
                    // hide before MandatoryOwnership made the authority load-bearing.
                    VILLAGE_TRADE -> true;
            default -> false;
        };
    }

    /** Read-only inspector attribution for a blocking activity class. */
    public static InvalidationCause invalidationCauseForActivity(ActivityClass activity) {
        return mandatoryCause(activity);
    }

    private static InvalidationCause mandatoryCause(ActivityClass activity) {
        return switch (activity) {
            case MANDATORY_COMMAND -> InvalidationCause.PLAYER_COMMAND;
            case MANDATORY_COMBAT -> InvalidationCause.COMBAT_TARGET;
            default -> InvalidationCause.MANDATORY_AUTHORITY;
        };
    }
}
