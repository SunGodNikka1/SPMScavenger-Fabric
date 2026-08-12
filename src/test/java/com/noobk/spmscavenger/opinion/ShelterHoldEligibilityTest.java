package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShelterHoldEligibilityTest {

    @Test
    void mandatoryNightHoldBlocksOpinionWhileOptionalRestDoesNot() {
        var hold = ActivityObservationService.summarize(List.of(ActivityClass.SHELTER_HOLD), true);
        var rest = ActivityObservationService.summarize(List.of(ActivityClass.REST), true);

        assertFalse(DiscretionaryEligibility.isDiscretionaryEligible(hold, false));
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY,
                DiscretionaryEligibility.invalidationForObservation(hold, false));
        assertTrue(DiscretionaryEligibility.isDiscretionaryEligible(rest, false));
    }
}
