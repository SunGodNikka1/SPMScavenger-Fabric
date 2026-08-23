package com.noobk.spmscavenger.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.debug.TeCurrencyWitnessTracker;
import com.noobk.spmscavenger.debug.TeCurrencyWitnessFixture;
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
                                .then(Commands.literal("prepare")
                                        .then(Commands.argument("mob", EntityArgument.entity())
                                                .executes(ctx -> prepare(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "mob"), false))))
                                .then(Commands.literal("run")
                                        .then(Commands.argument("mob", EntityArgument.entity())
                                                .executes(ctx -> prepare(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "mob"), true))))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource())))
                                .then(Commands.literal("report")
                                        .executes(ctx -> send(
                                                ctx.getSource(),
                                                TeCurrencyWitnessTracker.reportLines(),
                                                true)))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> stop(ctx.getSource(), false)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> stop(ctx.getSource(), true)))));
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
        if (result.armed()) {
            TeCurrencyWitnessFixture.markArmed(mob.getUUID(), backpack);
        } else {
            send(source, TeCurrencyWitnessFixture.cleanup(
                    source.getServer(), mob, "witness arm refused", false), true);
        }
        send(source, result.lines(), true);
        return result.armed() ? 1 : 0;
    }

    private static int prepare(CommandSourceStack source, Entity entity, boolean run) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal(
                    "Target must be a Social PlayerMob — W2 fixture did not mutate anything."));
            return 0;
        }
        TeCurrencyWitnessFixture.Result result = run
                ? TeCurrencyWitnessFixture.run(mob, source.getServer(), now(source))
                : TeCurrencyWitnessFixture.prepare(mob, source.getServer());
        send(source, result.lines(), result.success());
        return result.success() ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        send(source, TeCurrencyWitnessFixture.statusLines(), true);
        return send(source, TeCurrencyWitnessTracker.statusLines(), true);
    }

    private static int stop(CommandSourceStack source, boolean reset) {
        Mob mob = resolveFixtureTarget(source);
        List<String> fixture = TeCurrencyWitnessFixture.cleanup(
                source.getServer(), mob, reset ? "operator reset" : "operator stop", reset);
        List<String> tracker = reset
                ? TeCurrencyWitnessTracker.reset()
                : TeCurrencyWitnessTracker.stop("operator stopped witness", now(source));
        send(source, fixture, true);
        return send(source, tracker, true);
    }

    private static Mob resolveFixtureTarget(CommandSourceStack source) {
        // Cleanup itself verifies UUID + container identity; a nearby/foreign entity can never be
        // used as provenance. The fixture exposes no entity reference for this lookup.
        for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)) {
                    Container backpack = PlayerMobs.backpack(mob);
                    // markArmed is a no-op unless this is the exact fixture pair; status/cleanup
                    // performs the same identity test internally.
                    if (TeCurrencyWitnessFixture.matchesForCleanup(mob.getUUID(), backpack)) return mob;
                }
            }
        }
        return null;
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
