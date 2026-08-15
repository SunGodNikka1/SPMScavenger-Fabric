package com.noobk.spmscavenger.command;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Temporary VR-T1 read-only inspector for persisted village memory.
 *
 * <p>Must not call {@link com.noobk.spmscavenger.village.VillagePerception#observe}, load chunks,
 * or allocate memory — only {@link VillageMemorySavedData#peekMobMemory}.
 */
public final class VillageMemoryDebugCommand {

    private VillageMemoryDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("spmscavenger").requires(source -> source.hasPermission(2));
        dispatcher.register(root
                .then(Commands.literal("village-memory")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> printMemory(
                                        ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("village-probe")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> VillageProbeDebugCommand.probe(
                                        ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("village-driver")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> VillageDriverDebugCommand.printDriver(
                                        ctx.getSource(), EntityArgument.getEntity(ctx, "target"))))));
    }

    private static int printMemory(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal("Target must be a Social Player Mob."));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level."));
            return 0;
        }
        Optional<MobVillageMemory> memory =
                VillageMemorySavedData.peekMobMemory(level, mob.getUUID());
        sendReport(source, mob, level.dimension().location().toString(), memory);
        return 1;
    }

    private static void sendReport(
            CommandSourceStack source,
            Mob mob,
            String dimensionId,
            Optional<MobVillageMemory> memory) {
        source.sendSuccess(
                () -> Component.literal("Village Memory — PlayerMob " + mob.getName().getString()),
                false);
        source.sendSuccess(() -> Component.literal("Dimension: " + dimensionId), false);
        source.sendSuccess(() -> Component.literal(""), false);

        List<KnownVillage> villages = memory.map(MobVillageMemory::villages).orElse(List.of());
        if (villages.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Known villages: 0"), false);
            return;
        }

        List<KnownVillage> sorted = new ArrayList<>(villages);
        sorted.sort(Comparator.comparingLong(KnownVillage::lastSeenTick).reversed());

        source.sendSuccess(() -> Component.literal("Known villages: " + sorted.size()), false);
        source.sendSuccess(() -> Component.literal(""), false);

        for (int i = 0; i < sorted.size(); i++) {
            KnownVillage village = sorted.get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal("#" + index), false);
            source.sendSuccess(
                    () -> Component.literal("Anchor: " + formatAnchor(village.anchor())), false);
            source.sendSuccess(() -> Component.literal("Tier: " + village.tier().name()), false);
            source.sendSuccess(() -> Component.literal("POIs: " + village.poiCount()), false);
            source.sendSuccess(
                    () -> Component.literal("Coverage: " + formatCoverage(village.quality())), false);
            source.sendSuccess(() -> Component.literal("First seen: " + village.firstSeenTick()), false);
            source.sendSuccess(() -> Component.literal("Last seen: " + village.lastSeenTick()), false);
            if (i < sorted.size() - 1) {
                source.sendSuccess(() -> Component.literal(""), false);
            }
        }
    }

    static String formatAnchor(BlockPos anchor) {
        return anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ();
    }

    static String formatCoverage(ObservationQuality quality) {
        if (quality.isComplete()) {
            return "100%";
        }
        int total = quality.totalColumns();
        if (total <= 0) {
            return "100%";
        }
        int percent = (int) Math.round(100.0 * quality.loadedColumns() / total);
        return percent + "%";
    }
}
