package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.brigadier.StringReader;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves Social Player Mobs for temporary debug commands. Plain names like {@code Alice} work;
 * {@code @p} does not (PlayerMobs are entities, not server players).
 */
public final class PlayerMobDebugTargets {

    static final SuggestionProvider<CommandSourceStack> NAME_SUGGESTIONS = PlayerMobDebugTargets::suggestNames;

    private PlayerMobDebugTargets() {
    }

    public static Mob resolveNearest(CommandSourceStack source, double radius) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 pos = source.getPosition();
        AABB box = new AABB(pos, pos).inflate(radius);
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, PlayerMobs::isPlayerMob)) {
            double dist = mob.distanceToSqr(pos);
            if (dist < best) {
                best = dist;
                nearest = mob;
            }
        }
        return nearest;
    }

    public static Mob resolve(CommandSourceStack source, String target) throws CommandSyntaxException {
        String trimmed = target.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("@")) {
            EntitySelectorParser parser = new EntitySelectorParser(
                    new StringReader(trimmed), source.hasPermission(2));
            Entity entity = parser.parse().findSingleEntity(source);
            return asPlayerMob(entity);
        }
        if (!(source.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return resolveByName(level, trimmed);
    }

    static Mob resolveByName(ServerLevel level, String name) {
        AABB world = worldBounds(level);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, world, PlayerMobs::isPlayerMob)) {
            if (mob.getName().getString().equalsIgnoreCase(name)) {
                return mob;
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        if (source.getLevel() instanceof ServerLevel level) {
            AABB box = new AABB(source.getPosition(), source.getPosition()).inflate(256.0);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, PlayerMobs::isPlayerMob)) {
                builder.suggest(mob.getName().getString());
            }
        }
        return builder.buildFuture();
    }

    private static AABB worldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }

    static Mob asPlayerMob(Entity entity) {
        return entity instanceof Mob mob && PlayerMobs.isPlayerMob(mob) ? mob : null;
    }

    static String targetHelp() {
        return "No PlayerMob found. Use a mob name (Alice), @e[name=Alice,distance=..=256], "
                + "or omit the target for the nearest PlayerMob within 128 blocks.";
    }
}
