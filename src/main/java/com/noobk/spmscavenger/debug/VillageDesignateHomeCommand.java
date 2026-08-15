package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;

/**
 * Temporary VR-T1.5 fixtures (V1.5-F / D-VR-051). Removed after VR-T1.5 PASS.
 */
public final class VillageDesignateHomeCommand {

    private static final double NEAREST_RADIUS = 128.0;

    private VillageDesignateHomeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("designate-home")
                        .executes(context -> designate(context, null))
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests(PlayerMobDebugTargets.NAME_SUGGESTIONS)
                                .executes(context -> designate(
                                        context, StringArgumentType.getString(context, "target")))))
                .then(VillageSettlementStatusCommand.node()));
    }

    private static int designate(CommandContext<CommandSourceStack> context, String target)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Mob mob = target == null
                ? PlayerMobDebugTargets.resolveNearest(source, NEAREST_RADIUS)
                : PlayerMobDebugTargets.resolve(source, target);
        if (mob == null) {
            source.sendFailure(Component.literal(PlayerMobDebugTargets.targetHelp()));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level"));
            return 0;
        }
        var memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty() || memory.get().villages().isEmpty()) {
            source.sendFailure(Component.literal("Mob has no remembered villages"));
            return 0;
        }
        BlockPos anchor = memory.get().villages().stream()
                .min(Comparator.comparingDouble(v -> mob.blockPosition().distSqr(v.anchor())))
                .map(KnownVillage::anchor)
                .orElse(null);
        boolean ok = VillageMemorySavedData.get(level).designateHome(
                level,
                mob.getUUID(),
                anchor,
                level.getGameTime());
        if (!ok) {
            source.sendFailure(Component.literal("Home designation failed"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Designated home at " + anchor.toShortString()),
                true);
        return 1;
    }
}
