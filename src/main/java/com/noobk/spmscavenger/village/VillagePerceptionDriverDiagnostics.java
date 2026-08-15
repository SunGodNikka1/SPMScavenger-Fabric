package com.noobk.spmscavenger.village;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.goal.VillagePerceptionObserver;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.Optional;

/** VR-T1 read-only snapshot of V1-D observer + scheduler + last service trace for one mob. */
public final class VillagePerceptionDriverDiagnostics {

    public record ObserverSnapshot(
            boolean present,
            boolean running,
            boolean dirty,
            long lastEnqueueTick) {}

    public record Report(
            String mobName,
            ObserverSnapshot observer,
            boolean schedulerRegistered,
            boolean pendingRequest,
            int registeredObservers,
            int pendingRequests,
            long lastGlobalServiceTick,
            VillagePerceptionServiceTrace.Snapshot lastService) {}

    private VillagePerceptionDriverDiagnostics() {}

    public static Report capture(Mob mob, ServerLevel level) {
        VillagePerceptionScheduler scheduler =
                VillagePerceptionScheduler.forServer(level.getServer());
        Optional<VillagePerceptionServiceTrace.Snapshot> lastService =
                scheduler.lastServiceTrace(level.dimension(), mob.getUUID());
        return new Report(
                mob.getName().getString(),
                observerSnapshot(mob),
                scheduler.isObserverRegistered(mob.getUUID()),
                scheduler.hasPendingRequest(level.dimension(), mob.getUUID()),
                scheduler.registeredObserverCount(),
                scheduler.pendingCount(),
                scheduler.lastGlobalServiceTick(),
                lastService.orElse(null));
    }

    private static ObserverSnapshot observerSnapshot(Mob mob) {
        VillagePerceptionObserver observer = findObserver(mob).orElse(null);
        if (observer == null) {
            return new ObserverSnapshot(false, false, false, Long.MIN_VALUE);
        }
        boolean running = false;
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector != null) {
            for (WrappedGoal wrapped : selector.getAvailableGoals()) {
                if (wrapped.getGoal() == observer) {
                    running = wrapped.isRunning();
                    break;
                }
            }
        }
        return new ObserverSnapshot(
                true, running, observer.isDirtyForDiagnostics(), observer.lastEnqueueTickForDiagnostics());
    }

    private static Optional<VillagePerceptionObserver> findObserver(Mob mob) {
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        if (selector == null) {
            return Optional.empty();
        }
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagePerceptionObserver observer) {
                return Optional.of(observer);
            }
        }
        return Optional.empty();
    }
}
