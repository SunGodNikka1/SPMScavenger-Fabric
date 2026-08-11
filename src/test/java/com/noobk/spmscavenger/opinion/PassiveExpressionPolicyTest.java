package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveExpressionPolicyTest {

    @Test
    void disabledOrMeaningfulWorkProducesNoExpressionAuthority() {
        AffectiveState affect = state(80f, 80f, 80f, 80f, 80f);
        PersonalityModel personality = new PersonalityModel(1f, 1f, 1f, 1f, 1f, 1f);
        var idle = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));
        var work = ActivityObservationService.summarize(List.of(ActivityClass.PROJECT_EXECUTION));

        assertFalse(PassiveExpressionPolicy.evaluate(affect, personality, idle, false).eligible());
        assertFalse(PassiveExpressionPolicy.evaluate(affect, personality, work, true).eligible());
    }

    @Test
    void boredMobsShiftAttentionSoonerWithoutEscapingBounds() {
        var idle = ActivityObservationService.summarize(List.of(ActivityClass.IDLE_CANDIDATE));
        PersonalityModel neutral = PersonalityModel.NEUTRAL;
        PassiveExpressionProfile calm = PassiveExpressionPolicy.evaluate(
                state(0f, 0f, 0f, 0f, 0f), neutral, idle, true);
        PassiveExpressionProfile bored = PassiveExpressionPolicy.evaluate(
                state(0f, 100f, 0f, 0f, 0f), neutral, idle, true);

        assertTrue(bored.minCooldownTicks() < calm.minCooldownTicks());
        assertTrue(bored.maxCooldownTicks() < calm.maxCooldownTicks());
        assertValid(bored);
        assertValid(calm);
    }

    @Test
    void stressedMobsUseShorterAttentionHolds() {
        var resting = ActivityObservationService.summarize(
                List.of(ActivityClass.IDLE_CANDIDATE), true);
        PassiveExpressionProfile calm = PassiveExpressionPolicy.evaluate(
                state(0f, 0f, 0f, 0f, 0f), PersonalityModel.NEUTRAL, resting, true);
        PassiveExpressionProfile stressed = PassiveExpressionPolicy.evaluate(
                state(0f, 0f, 0f, 100f, 0f), PersonalityModel.NEUTRAL, resting, true);

        assertTrue(stressed.maxHoldTicks() < calm.maxHoldTicks());
        assertValid(stressed);
    }

    @Test
    void curiosityWidensOnlyTheCosmeticGazeEnvelope() {
        var exploring = ActivityObservationService.summarize(List.of(ActivityClass.EXPEDITION));
        PersonalityModel low = new PersonalityModel(0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
        PersonalityModel high = new PersonalityModel(1f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
        PassiveExpressionProfile narrow = PassiveExpressionPolicy.evaluate(
                state(0f, 0f, 0f, 0f, 0f), low, exploring, true);
        PassiveExpressionProfile wide = PassiveExpressionPolicy.evaluate(
                state(0f, 0f, 0f, 0f, 0f), high, exploring, true);

        assertTrue(wide.horizontalRangeDegrees() > narrow.horizontalRangeDegrees());
        assertTrue(wide.verticalRangeDegrees() > narrow.verticalRangeDegrees());
        assertValid(wide);
    }

    @Test
    void engagedExplorerKeepsTheCurrentTaskAttention() {
        var exploring = ActivityObservationService.summarize(List.of(ActivityClass.EXPEDITION));
        PassiveExpressionProfile profile = PassiveExpressionPolicy.evaluate(
                state(100f, 0f, 0f, 0f, 0f),
                new PersonalityModel(1f, 1f, 1f, 1f, 1f, 1f),
                exploring,
                true);

        assertFalse(profile.eligible(),
                "ENGAGED expression is non-interference with the executor's current gaze");
    }

    private static AffectiveState state(
            float engagement, float boredom, float satisfaction, float stress, float novelty) {
        AffectiveState state = new AffectiveState();
        state.seedChannels(engagement, boredom, satisfaction, stress, novelty);
        return state;
    }

    private static void assertValid(PassiveExpressionProfile profile) {
        assertTrue(profile.eligible());
        assertTrue(profile.minCooldownTicks() >= 1);
        assertTrue(profile.maxCooldownTicks() >= profile.minCooldownTicks());
        assertTrue(profile.minHoldTicks() >= 1);
        assertTrue(profile.maxHoldTicks() >= profile.minHoldTicks());
        assertTrue(Float.isFinite(profile.horizontalRangeDegrees()));
        assertTrue(Float.isFinite(profile.verticalRangeDegrees()));
        assertTrue(profile.socialLookChance() >= 0f && profile.socialLookChance() <= 1f);
    }
}
