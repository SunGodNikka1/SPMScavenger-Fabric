package com.noobk.spmscavenger.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Brigadier shape proof for the temporary operator witness branch. */
class TeCurrencyWitnessCommandsParseTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void allWitnessCommandsParseUnderTheExistingRoot() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VillageProfileCommands.register(dispatcher);
        CommandSourceStack source = new CommandSourceStack(
                net.minecraft.commands.CommandSource.NULL, Vec3.ZERO, Vec2.ZERO,
                (ServerLevel) null, 4, "test", Component.literal("test"),
                (MinecraftServer) null, null);

        for (String command : List.of(
                "spmscavenger debug te witness start @e[limit=1]",
                "spmscavenger debug te witness status",
                "spmscavenger debug te witness report",
                "spmscavenger debug te witness stop",
                "spmscavenger debug te witness reset")) {
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
            assertTrue(parsed.getExceptions().isEmpty(),
                    () -> command + " parse failed: " + parsed.getExceptions());
        }
    }
}
