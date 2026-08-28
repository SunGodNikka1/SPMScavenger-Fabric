package com.noobk.spmscavenger.validation;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Operator-only V4-G preparation/campaign surface in the validation sidecar. */
public final class V4RuntimeWitnessCommands {

    private V4RuntimeWitnessCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spmscavenger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("v4")
                                .then(Commands.literal("run")
                                        .executes(ctx -> V4RuntimeCampaignController.run(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> V4RuntimeCampaignController.status(ctx.getSource())))
                                .then(Commands.literal("report")
                                        .executes(ctx -> V4RuntimeCampaignController.report(ctx.getSource())))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> V4RuntimeCampaignController.stop(ctx.getSource())))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> V4RuntimeCampaignController.reset(ctx.getSource()))))));
    }
}
