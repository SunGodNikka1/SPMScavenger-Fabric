package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnership;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import com.noobk.spmscavenger.village.VillageWorkAdmission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Temporary Task-59 / V3-G one-shot runtime inspector.
 *
 * <p>This command snapshots existing production truth only. It owns no session, retains no entity
 * or level reference, and never invokes Goal admission/continuation or a production executor.
 */
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
                                                        EntityArgument.getEntity(ctx, "mob"))))))));
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

        long now = level.getGameTime();
        GoalSelector selector = ((MobGoalSelectorAccessor) mob).spmscavenger$getGoalSelector();
        MiningProjectSavedData mining = MiningProjectSavedData.get(level);
        ActivityObservationService.Observation observation =
                ActivityObservationService.observe(selector, mob, mining, now);
        Optional<MandatoryOwnershipClaim> claim =
                MandatoryOwnershipRegistry.liveClaim(mob.getUUID(), now);
        MandatoryOwnership.Permission authority = MandatoryOwnership.evaluate(
                observation, mob.getTarget() != null, claim, now);
        VillageScenarioProfile profile = PlayerMobVillagePolicySavedData.profileOf(
                source.getServer(), mob.getUUID());
        VillageWorkAdmission.Result villageWork = VillageWorkAdmission.evaluate(
                profile, observation, mob.getTarget() != null, claim, now);

        List<String> lines = new ArrayList<>();
        lines.add("=== V3 Runtime Witness Snapshot ===");
        lines.add("tick=" + now + " target=" + mob.getName().getString()
                + " uuid=" + mob.getUUID() + " dimension=" + level.dimension().location());
        lines.add("profile=" + profile.serialized() + " combatTarget=" + (mob.getTarget() != null));
        lines.add("running=" + runningGoals(selector, mob, mining, now));
        lines.add("activeClasses=" + observation.activeClasses());
        lines.add(claim.map(value -> "pendingClaim=YES consumer=" + value.consumerKey()
                        + " route=" + value.routeIdentity()
                        + " generation=" + value.generation()
                        + " openedAt=" + value.openedAt()
                        + " expiresAt=" + value.expiresAt()
                        + " ticksRemaining=" + Math.max(0L, value.expiresAt() - now))
                .orElse("pendingClaim=NO"));
        lines.add("mandatoryPermission=" + (authority.eligible() ? "ALLOW" : "DENY")
                + " cause=" + authority.cause());
        lines.add("villageWork=" + (villageWork.permitted() ? "ALLOW" : "DENY")
                + " cause=" + villageWork.cause()
                + " authorityCause=" + villageWork.authorityCause());

        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
            SpmScavenger.LOGGER.info("{} mob={} tick={} {}",
                    LOG_PREFIX, mob.getUUID(), now, line);
        }
        return 1;
    }

    private static List<String> runningGoals(
            GoalSelector selector,
            Mob mob,
            MiningProjectSavedData mining,
            long now) {
        List<String> out = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            String goal = wrapped.getGoal().getClass().getSimpleName();
            String activity = MoveHolderClassifier.activityClass(
                    wrapped.getGoal(), mob, mining, mob.getUUID(), now).name();
            out.add(goal + ":" + activity);
            if (out.size() >= 16) {
                break;
            }
        }
        return List.copyOf(out);
    }
}
