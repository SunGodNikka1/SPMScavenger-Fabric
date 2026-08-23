package com.noobk.spmscavenger.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.debug.TeCurrencyWitnessTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Temporary operator-only command surface for the V2-TE currency runtime witness. */
public final class TeCurrencyWitnessCommands {

    private TeCurrencyWitnessCommands() {
    }

    /** Branch under the existing permission-2 {@code /spmscavenger} root. */
    public static LiteralArgumentBuilder<CommandSourceStack> debugBranch() {
        return Commands.literal("debug")
                .then(Commands.literal("te")
                        .then(Commands.literal("witness")
                                .then(Commands.literal("start")
                                        .then(Commands.argument("mob", EntityArgument.entity())
                                                .executes(ctx -> start(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "mob")))))
                                .then(Commands.literal("status")
                                        .executes(ctx -> send(
                                                ctx.getSource(),
                                                TeCurrencyWitnessTracker.statusLines(),
                                                true)))
                                .then(Commands.literal("report")
                                        .executes(ctx -> send(
                                                ctx.getSource(),
                                                TeCurrencyWitnessTracker.reportLines(),
                                                true)))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> send(
                                                ctx.getSource(),
                                                TeCurrencyWitnessTracker.stop(
                                                        "operator stopped witness",
                                                        now(ctx.getSource())),
                                                true)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> send(
                                                ctx.getSource(),
                                                TeCurrencyWitnessTracker.reset(),
                                                true)))));
    }

    private static int start(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal(
                    "Target must be a Social PlayerMob — V2-TE witness not armed."));
            return 0;
        }
        Container backpack = PlayerMobs.backpack(mob);
        TeCurrencyWitnessTracker.ArmResult result =
                TeCurrencyWitnessTracker.arm(mob, backpack, now(source));
        send(source, result.lines(), true);
        return result.armed() ? 1 : 0;
    }

    private static int send(CommandSourceStack source, List<String> lines, boolean success) {
        for (String line : lines) {
            if (success) {
                source.sendSuccess(() -> Component.literal(line), false);
            } else {
                source.sendFailure(Component.literal(line));
            }
        }
        return success ? 1 : 0;
    }

    private static long now(CommandSourceStack source) {
        return source.getLevel() == null ? -1L : source.getLevel().getGameTime();
    }
}
