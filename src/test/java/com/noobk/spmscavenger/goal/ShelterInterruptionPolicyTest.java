package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShelterInterruptionPolicyTest {

    @Test
    void doorAndFiniteHelpersSuspendRatherThanCancel() {
        assertEquals(ShelterInterruptionPolicy.Decision.SUSPEND,
                ShelterInterruptionPolicy.decide(List.of(
                        ActivityClass.SOCIAL_REFLEX,
                        ActivityClass.PASSIVE_HELPER)));
    }

    @Test
    void commandsCombatFleeAndUnknownAuthorityCancel() {
        for (ActivityClass activity : List.of(
                ActivityClass.MANDATORY_COMMAND,
                ActivityClass.MANDATORY_COMBAT,
                ActivityClass.MANDATORY_SAFETY,
                ActivityClass.UNKNOWN_ACTIVE)) {
            assertEquals(ShelterInterruptionPolicy.Decision.CANCEL,
                    ShelterInterruptionPolicy.decide(List.of(activity)), activity.name());
        }
    }

    @Test
    void cancelDominatesBenignDoorEvidence() {
        assertEquals(ShelterInterruptionPolicy.Decision.CANCEL,
                ShelterInterruptionPolicy.decide(List.of(
                        ActivityClass.SOCIAL_REFLEX,
                        ActivityClass.MANDATORY_COMMAND)));
    }
}
