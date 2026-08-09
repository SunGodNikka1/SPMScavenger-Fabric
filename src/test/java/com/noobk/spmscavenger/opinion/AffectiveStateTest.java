package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.AffectPulse;
import com.noobk.spmscavenger.experience.ExperienceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffectiveStateTest {

    private static final int INTERVAL = 10;
    private static final UUID EPISODE = UUID.randomUUID();

    @Test
    void discretionaryIdleBuildsBoredomOverMinutesNotSeconds() {
        AffectiveState state = new AffectiveState();
        var idle = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));

        for (int i = 0; i < 120; i++) {
            state.observe(AffectiveObservation.from(idle, INTERVAL));
        }
        assertTrue(state.boredom() < 20f, "1 min idle should stay mostly neutral, was " + state.boredom());

        for (int i = 0; i < 240; i++) {
            state.observe(AffectiveObservation.from(idle, INTERVAL));
        }
        assertTrue(state.boredom() >= 25f, "3+ min idle should be restless, was " + state.boredom());
        assertTrue(state.boredom() < 90f, "should not spike to extreme in ~5 min, was " + state.boredom());
    }

    @Test
    void restRecoversStressWithMildEngagementAndSlowBoredom() {
        AffectiveState state = new AffectiveState();
        state.applyPulse(new AffectPulse(
                EPISODE, ExperienceKind.REST_SESSION, 0L,
                0f, 0f, 0f, 40f, 0f));
        float stressed = state.stress();

        var resting = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE), true);
        for (int i = 0; i < 40; i++) {
            state.observe(AffectiveObservation.from(resting, INTERVAL));
        }

        assertTrue(state.stress() < stressed, "REST should reduce stress");
        assertTrue(state.engagement() > 0f, "REST should grant mild engagement");
        assertTrue(state.boredom() < 15f, "REST boredom should rise slowly, was " + state.boredom());
    }

    @Test
    void socialTravelRelievesBoredom() {
        AffectiveState state = new AffectiveState();
        var idle = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));
        for (int i = 0; i < 200; i++) {
            state.observe(AffectiveObservation.from(idle, INTERVAL));
        }
        float bored = state.boredom();

        var social = ActivityObservationService.summarize(List.of(ActivityClass.SOCIAL_TRAVEL));
        for (int i = 0; i < 20; i++) {
            state.observe(AffectiveObservation.from(social, INTERVAL));
        }
        assertTrue(state.boredom() < bored, "social travel should flatline or decay boredom");
    }

    @Test
    void stalledOccupancyRaisesRestlessnessWithoutPreemptAuthority() {
        AffectiveState state = new AffectiveState();
        var stalled = ActivityObservationService.summarize(
                List.of(ActivityClass.UNKNOWN_ACTIVE));
        for (int i = 0; i < 300; i++) {
            state.observe(AffectiveObservation.from(stalled, INTERVAL));
        }
        assertTrue(state.boredom() > 2f, "stalled boredom was " + state.boredom());
    }

    @Test
    void frozenStateDoesNotCatchUpOnResume() {
        AffectiveState state = new AffectiveState();
        state.freeze();
        var idle = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));
        for (int i = 0; i < 500; i++) {
            state.observe(AffectiveObservation.from(idle, INTERVAL));
        }
        assertEquals(0f, state.boredom());
        state.resume();
        assertEquals(0f, state.boredom());
    }

    @Test
    void channelsDecayTowardNeutralWhenUntouched() {
        AffectiveState state = new AffectiveState();
        state.applyPulse(new AffectPulse(
                EPISODE, ExperienceKind.RESOURCE_HARVEST, 0L,
                20f, 0f, 15f, 10f, 8f));

        var social = ActivityObservationService.summarize(List.of(ActivityClass.SOCIAL_TRAVEL));
        for (int i = 0; i < 50; i++) {
            state.observe(AffectiveObservation.from(social, INTERVAL));
        }
        assertTrue(Math.abs(state.engagement()) < 20f);
        assertTrue(Math.abs(state.satisfaction()) < 15f);
        assertTrue(Math.abs(state.stress()) < 10f);
        assertTrue(Math.abs(state.novelty()) < 8f);
    }
}
