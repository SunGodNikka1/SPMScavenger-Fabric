package com.noobk.spmscavenger.goal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationReadinessTest {

    @Test
    void eitherLocalTripsOrSustainedIdleCanUnlockExploration() {
        ExplorationReadiness trips = new ExplorationReadiness();
        trips.recordSuccessfulLocalTrip();
        trips.recordSuccessfulLocalTrip();
        assertTrue(trips.eligible(100, 2, 600));

        ExplorationReadiness idle = new ExplorationReadiness();
        idle.recordIdleTicks(600);
        assertTrue(idle.eligible(100, 2, 600));
    }

    @Test
    void meaningfulWorkResetsBothActivationSignals() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordSuccessfulLocalTrip();
        readiness.recordIdleTicks(599);
        readiness.recordMeaningfulWork();
        assertFalse(readiness.eligible(100, 2, 600));
        assertTrue(readiness.successfulLocalTrips() == 0);
        assertTrue(readiness.idleWorkTicks() == 0);
    }

    @Test
    void consumingReadinessAppliesCooldownAndClearsSignals() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordIdleTicks(600);
        readiness.consume(200);
        readiness.recordIdleTicks(600);
        assertFalse(readiness.eligible(199, 2, 600));
        assertTrue(readiness.eligible(200, 2, 600));
    }
}
