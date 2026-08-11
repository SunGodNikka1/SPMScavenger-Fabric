package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveExpressionGoalContractTest {

    @Test
    void schedulerContractIsPriorityEightLookOnlyAndReadoutNoiseCompatible() {
        assertEquals(8, PassiveExpressionGoal.PRIORITY);
        assertEquals(EnumSet.of(Goal.Flag.LOOK), PassiveExpressionGoal.requiredFlags());
        assertTrue(RandomLookAroundGoal.class.isAssignableFrom(PassiveExpressionGoal.class),
                "SPM ObjectiveReadout filters RandomLookAroundGoal subclasses as noise");
        assertEquals(
                ActivityClass.PASSIVE_COSMETIC,
                MoveHolderClassifier.staticActivityClass(PassiveExpressionGoal.class));
        var observation = ActivityObservationService.summarize(
                java.util.List.of(ActivityClass.PASSIVE_COSMETIC));
        assertTrue(observation.discretionaryIdleCandidate(),
                "expression must not suppress readiness/affect idle semantics");
    }

    @Test
    void inclusiveSamplerCannotEscapeFiniteEpisodeBounds() {
        for (int value = -5; value <= 50; value++) {
            int sampled = PassiveExpressionGoal.clampSample(value, 8, 20);
            assertTrue(sampled >= 8 && sampled <= 20);
        }
    }

    @Test
    void bothMasterAndOpinionSwitchesAreRequired() {
        assertTrue(PassiveExpressionGoal.expressionEnabled(true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.expressionEnabled(false, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.expressionEnabled(true, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.expressionEnabled(false, false));
    }

    @Test
    void lostSocialTargetMustFallBackInsteadOfUsingStaleCoordinates() {
        assertTrue(PassiveExpressionGoal.socialTargetValid(true, true, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.socialTargetValid(false, true, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.socialTargetValid(true, false, true, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.socialTargetValid(true, true, false, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                PassiveExpressionGoal.socialTargetValid(true, true, true, false));
    }
}
