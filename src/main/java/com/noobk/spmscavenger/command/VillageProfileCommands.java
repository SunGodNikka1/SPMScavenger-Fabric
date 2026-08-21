package com.noobk.spmscavenger.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.command.VillageStorageCommands;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * D-VR-080 — explicit operator assignment for {@link VillageScenarioProfile}.
 */
public final class VillageProfileCommands {

    private VillageProfileCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("spmscavenger")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("village")
                                .then(Commands.literal("profile")
                                        .then(Commands.literal("get")
                                                .then(Commands.argument("mob", EntityArgument.entity())
                                                        .executes(ctx -> getProfile(ctx.getSource(),
                                                                EntityArgument.getEntity(ctx, "mob")))))
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("mob", EntityArgument.entity())
                                                        .then(Commands.argument("profile",
                                                                        StringArgumentType.word())
                                                                .executes(ctx -> setProfile(
                                                                        ctx.getSource(),
                                                                        EntityArgument.getEntity(ctx, "mob"),
                                                                        StringArgumentType.getString(ctx,
                                                                                "profile")))))))
                                .then(VillageStorageCommands.storageBranch())));
    }

    private static int getProfile(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal(
                    "Target must be a Social Player Mob — profile not read."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                level.getServer(), mob.getUUID());
        source.sendSuccess(() -> Component.literal(
                mob.getName().getString() + " village profile: " + profile.serialized()), false);
        return 1;
    }

    private static int setProfile(CommandSourceStack source, Entity entity, String rawProfile) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal(
                    "Target must be a Social Player Mob — profile not changed."));
            return 0;
        }
        VillageScenarioProfile profile = parseCommandProfile(rawProfile);
        if (profile == null) {
            source.sendFailure(Component.literal(
                    "Unknown profile '" + rawProfile + "'. Use neutral or village_ally."));
            return 0;
        }
        PlayerMobVillagePolicySavedData.setProfile(
                source.getServer(), mob.getUUID(), profile);
        source.sendSuccess(() -> Component.literal(
                mob.getName().getString() + " village profile set to " + profile.serialized()), true);
        return 1;
    }

    private static VillageScenarioProfile parseCommandProfile(String raw) {
        return switch (raw.toLowerCase()) {
            case "neutral" -> VillageScenarioProfile.NEUTRAL;
            case "village_ally" -> VillageScenarioProfile.VILLAGE_ALLY;
            default -> null;
        };
    }
}
