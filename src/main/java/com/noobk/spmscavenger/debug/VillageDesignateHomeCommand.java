package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;

/**
 * Temporary VR-T1.5 fixtures (V1.5-F / D-VR-051). Removed after VR-T1.5 PASS.
 */
public final class VillageDesignateHomeCommand {

    private VillageDesignateHomeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("designate-home")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> {
                                    Mob mob = asPlayerMob(EntityArgument.getEntity(context, "target"));
                                    if (mob == null) {
                                        context.getSource().sendFailure(
                                                Component.literal("Target is not a PlayerMob"));
                                        return 0;
                                    }
                                    if (!(mob.level() instanceof ServerLevel level)) {
                                        context.getSource().sendFailure(
                                                Component.literal("Target is not in a server level"));
                                        return 0;
                                    }
                                    var memory = VillageMemorySavedData.get(level)
                                            .peek(mob.getUUID());
                                    if (memory.isEmpty() || memory.get().villages().isEmpty()) {
                                        context.getSource().sendFailure(
                                                Component.literal("Mob has no remembered villages"));
                                        return 0;
                                    }
                                    var anchor = memory.get().villages().stream()
                                            .min(Comparator.comparingDouble(v -> mob.distanceToSqr(
                                                    v.anchor().getX() + 0.5,
                                                    v.anchor().getY(),
                                                    v.anchor().getZ() + 0.5)))
                                            .map(KnownVillage::anchor)
                                            .orElse(null);
                                    boolean ok = VillageMemorySavedData.get(level).designateHome(
                                            level,
                                            mob.getUUID(),
                                            anchor,
                                            level.getGameTime());
                                    if (!ok) {
                                        context.getSource().sendFailure(
                                                Component.literal("Home designation failed"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Designated home at "
                                                    + anchor.toShortString()),
                                            true);
                                    return 1;
                                })))
                .then(VillageSettlementStatusCommand.node()));
    }

    private static Mob asPlayerMob(net.minecraft.world.entity.Entity entity) {
        return entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob) ? mob : null;
    }
}
