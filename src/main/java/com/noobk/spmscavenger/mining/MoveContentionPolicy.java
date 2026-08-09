package com.noobk.spmscavenger.mining;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * MI-14C2 — detects when an actionable mining intent exists but another goal still holds
 * {@code MOVE} in the physical scheduler.
 */
public final class MoveContentionPolicy {

    private MoveContentionPolicy() {
    }

    /**
     * @param excluded running goal to ignore, usually the executor asking for admission
     */
    public static boolean hasBlockingMoveHolder(
            Mob mob,
            @Nullable Goal excluded,
            MiningProjectSavedData store,
            long now) {
        return SchedulerConflictPolicy.resolveBlocker(
                mob, excluded, store, now, Set.of(Goal.Flag.MOVE)) == ExecutionBlocker.CONTENTION;
    }

    /** @deprecated use {@link #hasBlockingMoveHolder} */
    @Deprecated
    public static boolean hasYieldingMoveHolder(
            Mob mob,
            @Nullable Goal excluded,
            MiningProjectSavedData store,
            long now) {
        return hasBlockingMoveHolder(mob, excluded, store, now);
    }
}
