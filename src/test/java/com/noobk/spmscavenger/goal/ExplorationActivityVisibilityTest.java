package com.noobk.spmscavenger.goal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import org.junit.jupiter.api.Test;

/** Regression coverage for Social Player Mobs' cosmetic-objective filter. */
final class ExplorationActivityVisibilityTest {

    @Test
    void activityObserverUsesTheHostCosmeticGoalContract() {
        assertTrue(RandomLookAroundGoal.class.isAssignableFrom(ExplorationActivityGoal.class),
                "The always-running bookkeeping observer must remain hidden by SPM's "
                        + "RandomLookAroundGoal objective filter");
    }

    @Test
    void villagePerceptionObserverUsesTheHostCosmeticGoalContract() {
        assertTrue(RandomLookAroundGoal.class.isAssignableFrom(VillagePerceptionObserver.class),
                "The always-running village perception observer must remain hidden by SPM's "
                        + "RandomLookAroundGoal objective filter");
    }

    @Test
    void anticsDecoratorUsesTheHostCosmeticGoalContract() {
        assertTrue(RandomLookAroundGoal.class.isAssignableFrom(AnticsGoal.class),
                "The always-running antics decorator must remain hidden by SPM's "
                        + "RandomLookAroundGoal objective filter");
    }
}
