package com.noobk.spmscavenger.command;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.VillagePerceptionDriverDiagnostics;
import com.noobk.spmscavenger.village.VillagePerceptionServiceTrace;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Temporary VR-T1 driver diagnostic — observer, scheduler, and last service trace. Does not observe
 * or record; read-only inspection of V1-D state.
 */
public final class VillageDriverDebugCommand {

    private VillageDriverDebugCommand() {}

    static int printDriver(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal("Target must be a Social Player Mob."));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level."));
            return 0;
        }

        VillagePerceptionDriverDiagnostics.Report report =
                VillagePerceptionDriverDiagnostics.capture(mob, level);
        sendReport(source, report);
        return 1;
    }

    private static void sendReport(CommandSourceStack source, VillagePerceptionDriverDiagnostics.Report report) {
        VillagePerceptionDriverDiagnostics.ObserverSnapshot observer = report.observer();

        source.sendSuccess(() -> Component.literal("Village Driver — " + report.mobName()), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(
                () -> Component.literal("Observer goal present: " + yesNo(observer.present())), false);
        source.sendSuccess(
                () -> Component.literal("Observer running: " + yesNo(observer.running())), false);
        source.sendSuccess(
                () -> Component.literal("Observer dirty: " + yesNo(observer.dirty())), false);
        source.sendSuccess(
                () -> Component.literal("Last enqueue tick: " + formatTick(observer.lastEnqueueTick())),
                false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(
                () -> Component.literal("Scheduler registered: " + yesNo(report.schedulerRegistered())),
                false);
        source.sendSuccess(
                () -> Component.literal("Pending request: " + yesNo(report.pendingRequest())), false);
        source.sendSuccess(
                () -> Component.literal("Registered observers: " + report.registeredObservers()), false);
        source.sendSuccess(
                () -> Component.literal("Pending requests: " + report.pendingRequests()), false);
        source.sendSuccess(() -> Component.literal(""), false);

        VillagePerceptionServiceTrace.Snapshot last = report.lastService();
        if (last == null) {
            source.sendSuccess(() -> Component.literal("Last service tick: NOT_RUN"), false);
            source.sendSuccess(() -> Component.literal("Entity resolved: NOT_RUN"), false);
            source.sendSuccess(() -> Component.literal("PlayerMob recognized: NOT_RUN"), false);
            source.sendSuccess(() -> Component.literal("Observed POIs: NOT_RUN"), false);
            source.sendSuccess(
                    () -> Component.literal("Record result: " + VillagePerceptionServiceTrace.RecordResult.NOT_RUN),
                    false);
            return;
        }

        source.sendSuccess(() -> Component.literal("Last service tick: " + last.serviceTick()), false);
        source.sendSuccess(
                () -> Component.literal("Entity resolved: " + yesNo(last.entityResolved())), false);
        source.sendSuccess(
                () -> Component.literal("PlayerMob recognized: " + yesNo(last.playerMobRecognized())),
                false);
        source.sendSuccess(() -> Component.literal("Observed POIs: " + last.observedPois()), false);
        source.sendSuccess(
                () -> Component.literal("Record result: " + formatRecordResult(last.recordResult())), false);
    }

    static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    static String formatTick(long tick) {
        return tick == Long.MIN_VALUE ? "never" : String.valueOf(tick);
    }

    static String formatRecordResult(VillagePerceptionServiceTrace.RecordResult result) {
        return switch (result) {
            case RECORDED -> "RECORDED";
            case EMPTY -> "EMPTY";
            case SKIPPED -> "SKIPPED";
            case NOT_RUN -> "NOT_RUN";
        };
    }
}
