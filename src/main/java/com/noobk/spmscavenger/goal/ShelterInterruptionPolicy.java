package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;

/** Pure scheduler-observation policy for an execution that has lost MOVE. */
final class ShelterInterruptionPolicy {

    enum Decision {
        SUSPEND,
        CANCEL
    }

    private ShelterInterruptionPolicy() {
    }

    static Decision decide(Iterable<ActivityClass> activeClasses) {
        for (ActivityClass activity : activeClasses) {
            if (activity == ActivityClass.MANDATORY_COMMAND
                    || activity == ActivityClass.MANDATORY_COMBAT
                    || activity == ActivityClass.MANDATORY_SAFETY
                    || activity == ActivityClass.UNKNOWN_ACTIVE) {
                return Decision.CANCEL;
            }
        }
        // Door operation and other finite/social helpers are execution interruptions, not evidence
        // that the already-selected shelter has become invalid.
        return Decision.SUSPEND;
    }
}
