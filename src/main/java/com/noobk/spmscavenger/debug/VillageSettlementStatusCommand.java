package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.SettlementRelationship;
import com.noobk.spmscavenger.village.SettlementTuning;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Comparator;

/**
 * Temporary VR-T1.5b read-only fixture (D-VR-051). Prints nearest remembered settlement
 * attachment state without mutating familiarity. Removed after VR-T1.5 PASS.
 */
public final class VillageSettlementStatusCommand {

    private static final double NEAREST_RADIUS = 128.0;

    private VillageSettlementStatusCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("settlement-status")
                .executes(context -> execute(context, null))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .suggests(PlayerMobDebugTargets.NAME_SUGGESTIONS)
                        .executes(context -> execute(
                                context, StringArgumentType.getString(context, "target"))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String target)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Mob mob = target == null
                ? PlayerMobDebugTargets.resolveNearest(source, NEAREST_RADIUS)
                : PlayerMobDebugTargets.resolve(source, target);
        if (mob == null) {
            source.sendFailure(Component.literal(PlayerMobDebugTargets.targetHelp()));
            return 0;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Target is not in a server level"));
            return 0;
        }
        var memoryOpt = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memoryOpt.isEmpty() || memoryOpt.get().villages().isEmpty()) {
            source.sendFailure(Component.literal("Mob has no remembered villages"));
            return 0;
        }
        MobVillageMemory memory = memoryOpt.get();
        KnownVillage nearest = memory.villages().stream()
                .min(Comparator.comparingDouble(v -> mob.blockPosition().distSqr(v.anchor())))
                .orElseThrow();
        BlockPos anchor = nearest.anchor();
        SettlementRelationship relationship = memory.relationshipAt(anchor)
                .orElse(SettlementRelationship.empty());
        boolean insideBounds = SettlementBoundsPolicy.within(mob.blockPosition(), anchor);

        String report = formatReport(
                mob.getName().getString(),
                anchor,
                relationship,
                nearest.isHome(),
                insideBounds);
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    static String formatReport(
            String mobName,
            BlockPos anchor,
            SettlementRelationship relationship,
            boolean home,
            boolean insideBounds) {
        return mobName + " settlement status\n"
                + "Nearest: " + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + "\n"
                + "Familiarity: " + relationship.familiarityScore()
                + " / " + SettlementTuning.MAX_FAMILIARITY + "\n"
                + "Band: " + relationship.attachmentBand().name() + "\n"
                + "Social events: " + relationship.socialEventCount() + "\n"
                + "Home: " + home + "\n"
                + "Last visit: " + relationship.lastVisitTick() + "\n"
                + "Inside bounds: " + insideBounds;
    }
}
