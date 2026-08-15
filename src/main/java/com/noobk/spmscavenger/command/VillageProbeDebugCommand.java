package com.noobk.spmscavenger.command;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.PerceptionCoverage;
import com.noobk.spmscavenger.village.VillagePerception;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Temporary VR-T1 active diagnostic — one-shot {@link VillagePerception#observe} at the mob's feet.
 *
 * <p>Unlike {@link VillageMemoryDebugCommand}, this deliberately performs a POI observation. It does
 * <b>not</b> write memory and must not be used as proof of autonomous V1-D discovery — only to split
 * perception/fixture failures from scheduler/record failures.
 */
public final class VillageProbeDebugCommand {

    private VillageProbeDebugCommand() {}

    static int probe(CommandSourceStack source, Entity entity) {
        boolean recognized = entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob);
        if (!recognized) {
            source.sendFailure(Component.literal("Target must be a Social Player Mob."));
            return 0;
        }
        Mob mob = (Mob) entity;
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level."));
            return 0;
        }

        BlockPos position = mob.blockPosition();
        VillagePerception.Observation observation = VillagePerception.observe(level, position);
        sendReport(source, mob.getName().getString(), position, observation);
        return 1;
    }

    private static void sendReport(
            CommandSourceStack source,
            String mobName,
            BlockPos position,
            VillagePerception.Observation observation) {
        source.sendSuccess(() -> Component.literal("Village Probe — " + mobName), false);
        source.sendSuccess(() -> Component.literal("PlayerMob recognized: YES"), false);
        source.sendSuccess(
                () -> Component.literal("Position: " + VillageMemoryDebugCommand.formatAnchor(position)),
                false);
        source.sendSuccess(
                () -> Component.literal("Coverage: " + formatCoverage(observation.coverage())),
                false);
        source.sendSuccess(
                () -> Component.literal("Admitted village POIs: " + observation.admittedPoiCount()),
                false);
        source.sendSuccess(
                () -> Component.literal("Settlement: " + (observation.isSettlement() ? "YES" : "NO")),
                false);
        source.sendSuccess(
                () -> Component.literal(
                        "Derived anchor: " + VillageMemoryDebugCommand.formatAnchor(observation.anchor())),
                false);
    }

    static String formatCoverage(PerceptionCoverage coverage) {
        if (coverage.isFull()) {
            return "100%";
        }
        int total = coverage.totalColumns();
        if (total <= 0) {
            return "100%";
        }
        int percent = (int) Math.round(100.0 * coverage.loadedColumns() / total);
        return percent + "%";
    }
}
