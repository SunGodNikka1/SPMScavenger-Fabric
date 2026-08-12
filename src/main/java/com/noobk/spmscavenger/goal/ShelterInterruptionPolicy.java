package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;

/**
 * SCR-2R5 pure policy. Semantic activity and physical interruption are separate dimensions.
 */
final class ShelterInterruptionPolicy {

    enum Decision {
        ALLOW_IN_PLACE,
        SUSPEND_AND_RESUME,
        OVERRIDE_AND_CANCEL,
        BLOCK_WHILE_SHELTERED
    }

    private ShelterInterruptionPolicy() {
    }

    static Decision decideInterruptedExecution(Iterable<ActivityClass> activeClasses) {
        for (ActivityClass activity : activeClasses) {
            if (activity == ActivityClass.MANDATORY_COMMAND
                    || activity == ActivityClass.MANDATORY_COMBAT
                    || activity == ActivityClass.MANDATORY_SAFETY) {
                return Decision.OVERRIDE_AND_CANCEL;
            }
        }
        // Unknown or voluntary movement is not permission to destroy the hold. The same bounded
        // commitment survives and reclaims MOVE after the incumbent finishes/is denied.
        return Decision.SUSPEND_AND_RESUME;
    }

    static Decision decideCandidate(ActivityClass activity, boolean displacing) {
        if (!displacing) {
            return Decision.ALLOW_IN_PLACE;
        }
        return switch (activity) {
            case MANDATORY_COMMAND, MANDATORY_COMBAT, MANDATORY_SAFETY ->
                    Decision.OVERRIDE_AND_CANCEL;
            case PASSIVE_HELPER -> Decision.SUSPEND_AND_RESUME;
            default -> Decision.BLOCK_WHILE_SHELTERED;
        };
    }
}
