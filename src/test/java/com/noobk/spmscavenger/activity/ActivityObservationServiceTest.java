package com.noobk.spmscavenger.activity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GAO-0 behavior-preservation tests for the shared observation reducer. */
class ActivityObservationServiceTest {

    @Test
    void mandatoryShelterCanProvideRestWithoutBecomingDiscretionaryRest() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.SHELTER_HOLD), true);

        assertTrue(observation.resting());
        assertTrue(observation.meaningfulWorkForExpedition());
        assertTrue(observation.schedulerOccupied());
        assertFalse(observation.discretionaryIdleCandidate());
        assertFalse(observation.activeClasses().contains(ActivityClass.REST));
    }

    @Test
    void pureLocalWanderingAndLookNoiseRemainIdle() {
        var observation = ActivityObservationService.summarize(List.of(
                ActivityClass.IDLE_CANDIDATE,
                ActivityClass.PASSIVE_COSMETIC,
                ActivityClass.PASSIVE_OBSERVER));

        assertTrue(observation.discretionaryIdleCandidate());
        assertFalse(observation.meaningfulWorkForExpedition());
        assertFalse(observation.exploring());
        // Local wandering owns scheduler flags even though it is an expedition-idle candidate.
        assertTrue(observation.schedulerOccupied());
    }

    @Test
    void followLovedOneRemainsMeaningfulRatherThanIdle() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.SOCIAL_TRAVEL));

        assertTrue(observation.meaningfulWorkForExpedition());
        assertTrue(observation.schedulerOccupied());
        assertFalse(observation.discretionaryIdleCandidate());
    }

    @Test
    void combatSafetyAndCommandsRemainMeaningful() {
        for (ActivityClass activity : List.of(
                ActivityClass.MANDATORY_COMBAT,
                ActivityClass.MANDATORY_SAFETY,
                ActivityClass.MANDATORY_COMMAND)) {
            var observation = ActivityObservationService.summarize(List.of(activity));
            assertTrue(observation.meaningfulWorkForExpedition(), activity.name());
            assertFalse(observation.discretionaryIdleCandidate(), activity.name());
        }
    }

    @Test
    void miningExecutionAndCooperativeWorkRemainMeaningful() {
        for (ActivityClass activity : List.of(
                ActivityClass.PROJECT_EXECUTION,
                ActivityClass.PRODUCTIVE_COOP,
                ActivityClass.SCAVENGE_WORK)) {
            var observation = ActivityObservationService.summarize(List.of(activity));
            assertTrue(observation.meaningfulWorkForExpedition(), activity.name());
            assertFalse(observation.discretionaryIdleCandidate(), activity.name());
        }
    }

    @Test
    void expeditionSuppressesIdleWithoutMasqueradingAsMeaningfulWork() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.EXPEDITION));

        assertTrue(observation.exploring());
        assertFalse(observation.meaningfulWorkForExpedition());
        assertFalse(observation.discretionaryIdleCandidate());
    }

    @Test
    void campfireAndShelterDoNotPrematurelyGainFutureRestClaimSemantics() {
        for (ActivityClass currentLifecycle : List.of(
                ActivityClass.REST_APPROACH,
                ActivityClass.MANDATORY_SAFETY)) {
            var observation = ActivityObservationService.summarize(List.of(currentLifecycle));
            assertTrue(observation.meaningfulWorkForExpedition(), currentLifecycle.name());
            assertFalse(observation.resting(), currentLifecycle.name());
        }

        var actualSleep = ActivityObservationService.summarize(List.of(ActivityClass.REST));
        assertTrue(actualSleep.resting());
        assertFalse(actualSleep.discretionaryIdleCandidate());
    }

    @Test
    void unknownRunningGoalIsFailSafeMeaningfulAndNotIdle() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.UNKNOWN_ACTIVE));

        assertTrue(observation.unknownActive());
        assertTrue(observation.meaningfulWorkForExpedition());
        assertFalse(observation.discretionaryIdleCandidate());
    }

    @Test
    void liveRestClaimSuppressesDiscretionaryIdle() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.IDLE_CANDIDATE), true);
        assertTrue(observation.resting());
        assertFalse(observation.discretionaryIdleCandidate());
    }

    @Test
    void passiveHelperPreservesLegacyMeaningfulWorkSemantics() {
        var observation = ActivityObservationService.summarize(
                List.of(ActivityClass.PASSIVE_HELPER));

        assertTrue(observation.meaningfulWorkForExpedition());
        assertFalse(observation.discretionaryIdleCandidate());
    }
}
