package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShelterInterruptionPolicyTest {

    @Test
    void finiteAndUnknownInterruptionsPreserveTheCommitmentForResume() {
        for (ActivityClass activity : List.of(
                ActivityClass.SOCIAL_REFLEX,
                ActivityClass.PASSIVE_HELPER,
                ActivityClass.UNKNOWN_ACTIVE)) {
            assertEquals(ShelterInterruptionPolicy.Decision.SUSPEND_AND_RESUME,
                    ShelterInterruptionPolicy.decideInterruptedExecution(List.of(activity)));
        }
    }

    @Test
    void commandsCombatAndPhysicalSafetyOverrideAndCancel() {
        for (ActivityClass activity : List.of(
                ActivityClass.MANDATORY_COMMAND,
                ActivityClass.MANDATORY_COMBAT,
                ActivityClass.MANDATORY_SAFETY)) {
            assertEquals(ShelterInterruptionPolicy.Decision.OVERRIDE_AND_CANCEL,
                    ShelterInterruptionPolicy.decideInterruptedExecution(List.of(activity)),
                    activity.name());
        }
    }

    @Test
    void candidatePolicySeparatesMeaningFromPhysicalDisplacement() {
        assertEquals(ShelterInterruptionPolicy.Decision.ALLOW_IN_PLACE,
                ShelterInterruptionPolicy.decideCandidate(ActivityClass.SOCIAL_REFLEX, false));
        assertEquals(ShelterInterruptionPolicy.Decision.BLOCK_WHILE_SHELTERED,
                ShelterInterruptionPolicy.decideCandidate(ActivityClass.SOCIAL_REFLEX, true));
        assertEquals(ShelterInterruptionPolicy.Decision.BLOCK_WHILE_SHELTERED,
                ShelterInterruptionPolicy.decideCandidate(ActivityClass.SCAVENGE_WORK, true));
        assertEquals(ShelterInterruptionPolicy.Decision.SUSPEND_AND_RESUME,
                ShelterInterruptionPolicy.decideCandidate(ActivityClass.PASSIVE_HELPER, true));
        assertEquals(ShelterInterruptionPolicy.Decision.OVERRIDE_AND_CANCEL,
                ShelterInterruptionPolicy.decideCandidate(ActivityClass.MANDATORY_COMMAND, true));
    }
}
