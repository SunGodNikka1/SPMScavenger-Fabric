package com.noobk.spmscavenger.mining;

import com.noobk.spmscavenger.goal.ControlledDescentGoal;
import com.noobk.spmscavenger.goal.CraftTorchesGoal;
import com.noobk.spmscavenger.goal.ExploringGoal;
import com.noobk.spmscavenger.goal.GatherResourcesGoal;
import com.noobk.spmscavenger.goal.SmeltAtFurnaceGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Optional;
import java.util.UUID;

/** Participating scavenger goals for MI-14C2 arbitration matrices. */
public enum MiningGoalKind {
    CONTROLLED_DESCENT,
    GATHER_RESOURCES,
    SMELT_AT_FURNACE,
    CRAFT_TORCHES,
    EXPLORING_ORDINARY,
    EXPLORING_CAVE_HANDOFF;

    public boolean isDesignatedConsumer() {
        return this == CONTROLLED_DESCENT || this == EXPLORING_CAVE_HANDOFF;
    }

    /**
     * Classifies a running or candidate goal. Returns empty for goals outside mining arbitration
     * (combat, survival, SPM host goals).
     */
    public static Optional<MiningGoalKind> classify(
            Goal goal, MiningProjectSavedData store, UUID mobId, long now) {
        if (goal instanceof ControlledDescentGoal) {
            return Optional.of(CONTROLLED_DESCENT);
        }
        if (goal instanceof GatherResourcesGoal) {
            return Optional.of(GATHER_RESOURCES);
        }
        if (goal instanceof SmeltAtFurnaceGoal) {
            return Optional.of(SMELT_AT_FURNACE);
        }
        if (goal instanceof CraftTorchesGoal) {
            return Optional.of(CRAFT_TORCHES);
        }
        if (goal instanceof ExploringGoal) {
            return Optional.of(classifyExploring(store, mobId, now));
        }
        return Optional.empty();
    }

    public static MiningGoalKind classifyExploring(
            MiningProjectSavedData store, UUID mobId, long now) {
        if (MiningTransition.acceptableCaveHandoff(
                        store.pendingTransition(mobId),
                        now,
                        ExecutionIntentPolicy.CAVE_HANDOFF_LIFETIME_TICKS)
                .isPresent()) {
            return EXPLORING_CAVE_HANDOFF;
        }
        return EXPLORING_ORDINARY;
    }
}
