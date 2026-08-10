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
        assertTrue(trips.eligibleForNewExpedition(100, 2, 600));

        ExplorationReadiness idle = new ExplorationReadiness();
        idle.recordIdleTicks(600);
        assertTrue(idle.eligibleForNewExpedition(100, 2, 600));
    }

    @Test
    void meaningfulWorkResetsBothActivationSignals() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordSuccessfulLocalTrip();
        readiness.recordIdleTicks(599);
        readiness.recordMeaningfulWork();
        assertFalse(readiness.eligibleForNewExpedition(100, 2, 600));
        assertTrue(readiness.successfulLocalTrips() == 0);
        assertTrue(readiness.idleWorkTicks() == 0);
    }

    @Test
    void consumingReadinessAppliesCooldownAndClearsSignals() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordIdleTicks(600);
        readiness.consume(200);
        readiness.recordIdleTicks(600);
        assertFalse(readiness.eligibleForNewExpedition(199, 2, 600));
        assertTrue(readiness.eligibleForNewExpedition(200, 2, 600));
    }

    @Test
    void descentPressureUnlocksExploreWithoutIdleTrips() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordDescentPressure();
        assertTrue(readiness.hasDescentPressure());
        assertTrue(readiness.eligibleForNewExpedition(100, 2, 600));
        readiness.consume(200);
        // MI-5 defect 1: consume() no longer clears descent pressure. That field is owned solely by
        // ExplorationActivityGoal.updateDescentPressure, which re-derives it every observation tick.
        // Clearing it here ran before the expedition's first plan, so the stage that sets the
        // heading never sorted for descent.
        assertTrue(readiness.hasDescentPressure(),
                "pressure survives consume; the observer owns its lifecycle");
        // The safety property that makes that acceptable: cooldown alone still blocks re-entry.
        assertFalse(readiness.eligibleForNewExpedition(100, 2, 600), "cooldown must still gate a new expedition");
    }

    @Test
    void descentPressureAloneCannotLoopExpeditionsInsideCooldown() {
        ExplorationReadiness readiness = new ExplorationReadiness();
        readiness.recordDescentPressure();
        readiness.consume(500);
        for (long now = 0; now < 500; now += 50) {
            assertFalse(readiness.eligibleForNewExpedition(now, 2, 600),
                    "retained pressure must not bypass the cooldown at tick " + now);
        }
        assertTrue(readiness.eligibleForNewExpedition(500, 2, 600), "and it re-arms once the cooldown expires");
    }
}
