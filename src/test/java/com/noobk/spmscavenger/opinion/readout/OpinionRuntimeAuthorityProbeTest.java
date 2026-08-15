package com.noobk.spmscavenger.opinion.readout;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.opinion.InvalidationCause;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpinionRuntimeAuthorityProbeTest {

    @Test
    void mustHappen_firstBlockingRunningGoalIsAttributedInSelectorOrder() {
        List<RunningGoalView> running = List.of(
                new RunningGoalView("SkepticalWatchGoal", ActivityClass.SOCIAL_REFLEX.name()),
                new RunningGoalView("DoorOperationGoal", ActivityClass.SOCIAL_REFLEX.name()));

        var blocker = OpinionRuntimeAuthorityProbe.attributeBlocker(
                running,
                ActivityObservationService.summarize(List.of(ActivityClass.SOCIAL_REFLEX)),
                false);

        assertEquals("SkepticalWatchGoal", blocker.goalSimpleName());
        assertEquals(ActivityClass.SOCIAL_REFLEX.name(), blocker.activityClass());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY.name(), blocker.cause());
    }

    @Test
    void mustHappen_combatTargetTakesPrecedenceOverRunningGoals() {
        List<RunningGoalView> running = List.of(
                new RunningGoalView("DoorOperationGoal", ActivityClass.SOCIAL_REFLEX.name()));

        var blocker = OpinionRuntimeAuthorityProbe.attributeBlocker(
                running,
                ActivityObservationService.summarize(List.of(ActivityClass.SOCIAL_REFLEX)),
                true);

        assertEquals("COMBAT_TARGET", blocker.activityClass());
        assertEquals(InvalidationCause.COMBAT_TARGET.name(), blocker.cause());
    }
}
