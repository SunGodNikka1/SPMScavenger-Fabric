package com.noobk.spmscavenger.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.storage.GrantSnapshot;
import com.noobk.spmscavenger.village.storage.ResolvedContainer;
import com.noobk.spmscavenger.village.storage.ResolvedContainerFacts;
import com.noobk.spmscavenger.village.storage.SettlementStorageFact;
import com.noobk.spmscavenger.village.storage.SettlementStorageFactSource;
import com.noobk.spmscavenger.village.storage.StorageContainerResolver;
import com.noobk.spmscavenger.village.storage.StorageOwnership;
import com.noobk.spmscavenger.village.storage.StorageOwnershipPolicy;
import com.noobk.spmscavenger.village.storage.StoragePermissionSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * D-VR-081 — operator explicit storage permission commands (gen-1).
 */
public final class VillageStorageCommands {

    private VillageStorageCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> storageBranch() {
        return Commands.literal("storage")
                .then(Commands.literal("get")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> getStorage(
                                        ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                        null))
                                .then(Commands.argument("mob", EntityArgument.entity())
                                        .executes(ctx -> getStorage(
                                                ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                                EntityArgument.getEntity(ctx, "mob"))))))
                .then(Commands.literal("own")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> ownStorage(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "mob"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                .then(Commands.literal("share")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> shareStorage(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "mob"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                .then(Commands.literal("unshare")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> unshareStorage(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "mob"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> revokeStorage(
                                        ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("revoke-key")
                        .then(Commands.argument("dimension", StringArgumentType.greedyString())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> revokeKey(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "dimension"),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .executes(ctx -> listStorage(
                                        ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "mob")))));
    }

    private static int getStorage(CommandSourceStack source, BlockPos pos, Entity mobEntity) {
        ServerLevel level = source.getLevel();
        UUID mobId = mobEntity instanceof Mob mob && PlayerMobs.isPlayerMob(mob)
                ? mob.getUUID()
                : null;
        if (!StorageContainerResolver.isChunkLoaded(level, pos)) {
            source.sendSuccess(() -> Component.literal(
                    pos.toShortString() + " storage: chunk unloaded — ownership UNKNOWN"), false);
            return 1;
        }
        ResolvedContainerFacts facts = StorageContainerResolver.facts(level, pos);
        SettlementStorageFact settlement = SettlementStorageFactSource.resolve(level, pos);
        GrantSnapshot grants = StoragePermissionSavedData.peek(source.getServer());
        StorageOwnership ownership = StorageOwnershipPolicy.classify(
                facts, settlement, mobId, grants);
        source.sendSuccess(() -> Component.literal(
                pos.toShortString() + " storage ownership: " + ownership.name().toLowerCase()
                        + " (settlement=" + settlement.name().toLowerCase() + ")"), false);
        return 1;
    }

    private static int ownStorage(CommandSourceStack source, Entity entity, BlockPos pos) {
        Mob mob = requirePlayerMob(source, entity);
        if (mob == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Optional<ResolvedContainer> resolved = resolveForMutation(source, level, pos);
        if (resolved.isEmpty()) {
            return 0;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.get(source.getServer());
        data.grantOwner(resolved.get().canonicalGlobal(), mob.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Granted mob-owned storage at " + resolved.get().canonicalGlobal()), true);
        return 1;
    }

    private static int shareStorage(CommandSourceStack source, Entity entity, BlockPos pos) {
        Mob mob = requirePlayerMob(source, entity);
        if (mob == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Optional<ResolvedContainer> resolved = resolveForMutation(source, level, pos);
        if (resolved.isEmpty()) {
            return 0;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.get(source.getServer());
        data.addShare(resolved.get().canonicalGlobal(), mob.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Shared storage at " + resolved.get().canonicalGlobal() + " with "
                        + mob.getName().getString()), true);
        return 1;
    }

    private static int unshareStorage(CommandSourceStack source, Entity entity, BlockPos pos) {
        Mob mob = requirePlayerMob(source, entity);
        if (mob == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (!StorageContainerResolver.isChunkLoaded(level, pos)) {
            source.sendFailure(Component.literal(
                    "Chunk unloaded — use revoke-key with list output for exact GlobalPos."));
            return 0;
        }
        Optional<ResolvedContainer> resolved = StorageContainerResolver.resolveLoaded(level, pos);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal("No lootable container at loaded position."));
            return 0;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(source.getServer());
        if (data == null || !data.removeShare(resolved.get().canonicalGlobal(), mob.getUUID())) {
            source.sendFailure(Component.literal("No share entry for that mob at canonical key."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Removed share for " + mob.getName().getString() + " at "
                        + resolved.get().canonicalGlobal()), true);
        return 1;
    }

    private static int revokeStorage(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        if (!StorageContainerResolver.isChunkLoaded(level, pos)) {
            source.sendFailure(Component.literal(
                    "Chunk unloaded — use revoke-key with list output for exact GlobalPos."));
            return 0;
        }
        Optional<ResolvedContainer> resolved = StorageContainerResolver.resolveLoaded(level, pos);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No lootable container at loaded position — use revoke-key for stale rows."));
            return 0;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(source.getServer());
        if (data == null || !data.revokeKey(resolved.get().canonicalGlobal())) {
            source.sendFailure(Component.literal("No grant at canonical key."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Revoked storage grant at " + resolved.get().canonicalGlobal()), true);
        return 1;
    }

    private static int revokeKey(
            CommandSourceStack source, String dimensionId, int x, int y, int z) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            source.sendFailure(Component.literal("Invalid dimension id: " + dimensionId));
            return 0;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        GlobalPos key = GlobalPos.of(dimension, new BlockPos(x, y, z));
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(source.getServer());
        if (data == null || !data.revokeKey(key)) {
            source.sendFailure(Component.literal("No grant at " + key));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Revoked storage grant at " + key), true);
        return 1;
    }

    private static int listStorage(CommandSourceStack source, Entity entity) {
        Mob mob = requirePlayerMob(source, entity);
        if (mob == null) {
            return 0;
        }
        StoragePermissionSavedData data = StoragePermissionSavedData.peek(source.getServer());
        List<GlobalPos> keys = data == null ? List.of() : data.listForMob(mob.getUUID());
        if (keys.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    mob.getName().getString() + " has no explicit storage grants."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                mob.getName().getString() + " storage grants: " + keys), false);
        return 1;
    }

    private static Optional<ResolvedContainer> resolveForMutation(
            CommandSourceStack source, ServerLevel level, BlockPos pos) {
        if (!StorageContainerResolver.isChunkLoaded(level, pos)) {
            source.sendFailure(Component.literal(
                    "Chunk must be loaded — will not load or generate chunks."));
            return Optional.empty();
        }
        Optional<ResolvedContainer> resolved = StorageContainerResolver.resolveLoaded(level, pos);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Target must be a loaded lootable container (chest, barrel, or shulker)."));
            return Optional.empty();
        }
        return resolved;
    }

    private static Mob requirePlayerMob(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof Mob mob) || !PlayerMobs.isPlayerMob(mob)) {
            source.sendFailure(Component.literal("Target must be a Social Player Mob."));
            return null;
        }
        return mob;
    }
}
