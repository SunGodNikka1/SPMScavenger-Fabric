package com.noobk.spmscavenger.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.MandatoryOwnership;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.mining.MoveHolderClassifier;
import com.noobk.spmscavenger.mixin.MobGoalSelectorAccessor;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageScenarioProfile;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.VillageWorkAdmission;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.VillageWorkFactsService;
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
        Gate0Snapshot gate0 = captureGate0(level, mob);
        boolean daytime = level.isDay();
        boolean shelterHold = observation.activeClasses().contains(ActivityClass.SHELTER_HOLD);
        V3RowPrecondition.Result rowPrecondition =
                V3RowPrecondition.evaluate(daytime, shelterHold);

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
        lines.add("settlement observed: " + (gate0.settlement().isPresent() ? "YES" : "NO"));
        lines.add(gate0.settlement()
                .map(value -> "settlement anchor: " + value.anchor().toShortString()
                        + " settlement identity: " + value.identity())
                .orElse("settlement anchor: UNAVAILABLE settlement identity: UNAVAILABLE"));
        lines.add(gate0.facts()
                .map(value -> "populationFacts adultVillagerCount=" + value.adultVillagerCount()
                        + " totalUsableHomeCapacity=" + value.totalUsableHomeCapacity()
                        + " claimedHomeCount=" + value.claimedHomeCount()
                        + " currentFreeHomeCapacity=" + value.currentFreeHomeCapacity()
                        + " completeness=" + value.completeness()
                        + " freshness=" + value.freshness()
                        + " observedAtTick=" + value.observedAtTick())
                .orElse("populationFacts=UNAVAILABLE completeness=UNAVAILABLE freshness=UNAVAILABLE"));
        lines.add("Gate0=" + gate0.assessment().verdict()
                + " reason=" + gate0.assessment().reason());
        lines.add("daytime=" + (daytime ? "YES" : "NO")
                + " dayTime=" + level.getDayTime()
                + " shelterHold=" + (shelterHold ? "YES" : "NO"));
        lines.add("RowPrecondition=" + rowPrecondition.verdict()
                + " reason=" + rowPrecondition.reason());

        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
            SpmScavenger.LOGGER.info("{} mob={} tick={} {}",
                    LOG_PREFIX, mob.getUUID(), now, line);
        }
        return 1;
    }

    private static Gate0Snapshot captureGate0(ServerLevel level, Mob mob) {
        VillageMemorySavedData memory = VillageMemorySavedData.peekInDimension(level);
        Optional<MobVillageMemory> mobMemory = memory == null
                ? Optional.empty()
                : memory.peek(mob.getUUID());
        Optional<RememberedSettlement> settlement = mobMemory.flatMap(value ->
                nearestCurrentSettlement(value, mob.blockPosition(), level));
        Optional<VillageWorkFacts> facts = settlement.flatMap(value ->
                VillageWorkFactsService.peekReadOnly(level, value.identity()));
        V3Gate0Assessment.Result assessment =
                V3Gate0Assessment.evaluate(settlement.isPresent(), facts);
        return new Gate0Snapshot(settlement, facts, assessment);
    }

    private static Optional<RememberedSettlement> nearestCurrentSettlement(
            MobVillageMemory memory,
            net.minecraft.core.BlockPos mobPos,
            ServerLevel level) {
        KnownVillage nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (KnownVillage village : memory.villages()) {
            if (!SettlementBoundsPolicy.within(mobPos, village.anchor())) {
                continue;
            }
            double distance = mobPos.distSqr(village.anchor());
            if (distance < nearestDistance) {
                nearest = village;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }
        SettlementIdentity identity = SettlementIdentity.of(level.dimension(), nearest.anchor());
        return Optional.of(new RememberedSettlement(nearest.anchor(), identity));
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

    private record RememberedSettlement(
            net.minecraft.core.BlockPos anchor,
            SettlementIdentity identity) {
    }

    private record Gate0Snapshot(
            Optional<RememberedSettlement> settlement,
            Optional<VillageWorkFacts> facts,
            V3Gate0Assessment.Result assessment) {
    }
}
