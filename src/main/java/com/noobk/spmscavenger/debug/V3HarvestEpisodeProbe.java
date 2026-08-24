package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.goal.VillageHarvestEpisodeGoal;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Temporary read-only probe of the production harvest episode already installed on a mob. */
final class V3HarvestEpisodeProbe {

    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final Field PHASE = field("phase");
    private static final Field TARGET = field("targetPos");

    record Snapshot(boolean installed, boolean running, String phase, Optional<BlockPos> target) {
        Snapshot {
            target = target.map(BlockPos::immutable);
        }
    }

    private V3HarvestEpisodeProbe() {
    }

    static Snapshot peek(Mob mob) {
        try {
            for (WrappedGoal wrapped : ((MobGoalSelectorAccessor) mob)
                    .spmscavenger$getGoalSelector().getAvailableGoals()) {
                if (!(wrapped.getGoal() instanceof VillageHarvestEpisodeGoal goal)) {
                    continue;
                }
                if (PHASE == null || TARGET == null) {
                    return new Snapshot(
                            true, wrapped.isRunning(), "UNAVAILABLE", Optional.empty());
                }
                Object phase = PHASE.get(goal);
                BlockPos target = (BlockPos) TARGET.get(goal);
                return new Snapshot(true, wrapped.isRunning(), String.valueOf(phase),
                        Optional.ofNullable(target));
            }
        } catch (IllegalAccessException | RuntimeException failure) {
            logFailure(failure);
            return new Snapshot(false, false, "UNAVAILABLE", Optional.empty());
        }
        return new Snapshot(false, false, "NOT_INSTALLED", Optional.empty());
    }

    private static Field field(String name) {
        try {
            Field field = VillageHarvestEpisodeGoal.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logFailure(failure);
            return null;
        }
    }

    private static void logFailure(Throwable failure) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger/v3-campaign] harvest episode probe unavailable", failure);
        }
    }
}
