package com.noobk.spmscavenger.validation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Temporary Task-59 one-shot inspector and campaign command surface. */
public final class V3RuntimeWitnessCommands {

    private static final String LOG_PREFIX = "[spmscavenger/v3-witness]";

    private V3RuntimeWitnessCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("v3")
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("mob", EntityArgument.entity())
                                                .executes(ctx -> inspect(
                                                        ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "mob")))))
                                .then(Commands.literal("run")
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (V3CampaignScenario scenario
                                                            : V3CampaignScenario.values()) {
                                                        builder.suggest(scenario.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> V3RuntimeCampaignController.run(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(
                                                                ctx, "preset")))))
                                .then(Commands.literal("status")
                                        .executes(ctx -> V3RuntimeCampaignController.status(
                                                ctx.getSource())))
                                .then(Commands.literal("report")
                                        .executes(ctx -> V3RuntimeCampaignController.report(
                                                ctx.getSource())))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> V3RuntimeCampaignController.stop(
                                                ctx.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> V3RuntimeCampaignController.reset(
                                                ctx.getSource()))))));
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal(
                    "Target must be a Social Player Mob — V3 witness not captured."));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal(
                    "Target is not in a server level — V3 witness not captured."));
            return 0;
        }

        V3WitnessSnapshot snapshot = V3WitnessSnapshot.capture(level, mob);
        for (String line : snapshot.lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
            SpmScavenger.LOGGER.info("{} mob={} tick={} {}",
                    LOG_PREFIX, mob.getUUID(), snapshot.tick(), line);
        }
        return 1;
    }
}
