package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Permanent, passive one-shot production inspector. */
public final class GeneralDebugCommands {

    private static final String LOG_PREFIX = "[spmscavenger/debug]";

    private GeneralDebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("mob", EntityArgument.entity())
                                        .executes(context -> inspect(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "mob")))))));
    }

    private static int inspect(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal("Target must be a Social Player Mob."));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level."));
            return 0;
        }
        GeneralDebugSnapshot snapshot = GeneralDebugSnapshot.capture(level, mob);
        for (String line : snapshot.lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
            SpmScavenger.LOGGER.info("{} mob={} tick={} {}",
                    LOG_PREFIX, mob.getUUID(), snapshot.tick(), line);
        }
        return 1;
    }
}
