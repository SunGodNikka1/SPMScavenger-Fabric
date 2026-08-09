package com.noobk.spmscavenger.mining;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * MI-14C2 — goal-facing admission and continuation checks against the shared arbiter.
 */
public final class MiningExecutionGuard {

    private MiningExecutionGuard() {
    }

    /** Shared policy for {@code canUse} and {@code canContinueToUse}. */
    public static boolean permits(Mob mob, Goal self, MiningGoalKind kind) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return true;
        }
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        long now = level.getGameTime();
        ExecutionIntent intent = ExecutionIntentPolicy.derive(store, mob.getUUID(), now);
        ArbitrationDecision decision = MiningExecutionArbiter.decide(intent, kind);
        if (kind.isDesignatedConsumer()) {
            return decision.permitsDesignatedConsumer();
        }
        return decision.permitsAdmission();
    }
}
