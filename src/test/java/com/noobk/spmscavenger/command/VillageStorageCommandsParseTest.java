package com.noobk.spmscavenger.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.mojang.brigadier.CommandDispatcher;

/** Task-54 R1-5 — Brigadier parse proof for revoke-key dimension tokenization. */
class VillageStorageCommandsParseTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void revokeKeyParsesNamespacedDimensionAndCoordinates() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VillageProfileCommands.register(dispatcher);
        CommandSourceStack source = new CommandSourceStack(
                net.minecraft.commands.CommandSource.NULL,
                Vec3.ZERO,
                Vec2.ZERO,
                (ServerLevel) null,
                4,
                "test",
                Component.literal("test"),
                (MinecraftServer) null,
                null);
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(
                "spmscavenger village storage revoke-key minecraft:overworld 8 64 8",
                source);
        assertTrue(parsed.getExceptions().isEmpty(),
                () -> "parse failed: " + parsed.getExceptions());
    }
}
