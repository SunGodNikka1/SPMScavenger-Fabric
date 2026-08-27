package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.opinion.DiscretionaryScoringInput;
import com.noobk.spmscavenger.opinion.SettlementOpinionContext;
import com.noobk.spmscavenger.opinion.SettlementOpinionInputs;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.TradeOutputCapability;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.intent.VillageIntentEvaluation;
import com.noobk.spmscavenger.village.intent.VillageIntentFacts;
import com.noobk.spmscavenger.village.intent.VillageIntentRegistry;
import com.noobk.spmscavenger.village.routing.CapabilityEvidenceClass;
import com.noobk.spmscavenger.village.routing.RouteAttemptEvidence;
import com.noobk.spmscavenger.village.routing.SettlementDestinationFacts;
import com.noobk.spmscavenger.village.routing.SettlementDestinationRanker;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import com.noobk.spmscavenger.village.trade.TradePurchaseProjection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * D-VR-091/V4-E — production settlement orchestration facade.
 *
 * <p>This class assembles current demand, existing-route evidence, persistent positive trader
 * memory, factual ranking and bounded Opinion. It can open/revalidate a transient intent and return
 * movement admission. It owns no Goal, path, merchant scan, offer, affordability result, or
 * transaction authority.
 */
public final class VillageInteractionDirector {

    private VillageInteractionDirector() {
    }

    /** Opens a fresh commitment or resumes the current one only after live revalidation. */
    public static Optional<CommuteDirective> openOrResumeRequiredTrade(
            ServerLevel level, PathfinderMob mob) {
        if (level == null || mob == null) {
            return Optional.empty();
        }
        ResolvedFacts resolved = resolve(level, mob, false);
        return openOrResumeResolved(
                mob.getUUID(),
                level.dimension(),
                mob.blockPosition(),
                resolved,
                VillageRouteAttemptRegistry.snapshot(mob.getUUID(), level.getGameTime()),
                level.getGameTime());
    }

    static Optional<CommuteDirective> openOrResumeResolved(
            UUID mobId,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            net.minecraft.core.BlockPos origin,
            ResolvedFacts resolved,
            RouteAttemptEvidence attempts,
            long now) {
        // Existence is diagnostic only. The result below, not this read, is the authority boundary.
        boolean hadIntent = VillageIntentRegistry.current(mobId).isPresent();
        VillageIntentEvaluation evaluation =
                VillageIntentRegistry.revalidate(mobId, resolved.intentFacts());
        if (evaluation.intentStillExists()) {
            if (!evaluation.currentlyAdmissible()) {
                return Optional.empty();
            }
            return postRevalidationDirective(mobId);
        }
        if (hadIntent) {
            // Closing and reselection stay separate boundaries, preserving V4-D hysteresis.
            return Optional.empty();
        }

        Optional<SettlementDestinationRanker.Selection> selected = SettlementDestinationRanker.select(
                resolved.candidates(),
                dimension,
                origin,
                resolved.opinionInput(),
                resolved.opinionContext(),
                attempts,
                now);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return VillageIntentRegistry.openRequiredTrade(
                        mobId, resolved.intentFacts(), selected.get(), now)
                .map(CommuteDirective::requiredTrade);
    }

    /** Exact-binding revalidation used by the movement executor at continuation/resume boundaries. */
    public static CommuteDirectiveEvaluation revalidateRequiredTrade(
            ServerLevel level,
            PathfinderMob mob,
            CommuteDirective.Binding expected,
            boolean interrupted) {
        if (level == null || mob == null || expected == null) {
            return CommuteDirectiveEvaluation.closed("MISSING_BOUNDARY_INPUT");
        }
        ResolvedFacts resolved = resolve(level, mob, interrupted);
        VillageIntentEvaluation evaluation =
                VillageIntentRegistry.revalidate(mob.getUUID(), resolved.intentFacts());
        if (!evaluation.intentStillExists()) {
            return CommuteDirectiveEvaluation.closed(evaluation.cause().name());
        }
        Optional<VillageIntent> current = VillageIntentRegistry.current(mob.getUUID());
        if (current.isEmpty() || !expected.matchesExact(current.get())) {
            return CommuteDirectiveEvaluation.closed("BINDING_REPLACED");
        }
        if (!evaluation.currentlyAdmissible()) {
            return CommuteDirectiveEvaluation.interrupted(evaluation.cause().name());
        }
        return CommuteDirectiveEvaluation.active(CommuteDirective.requiredTrade(current.get()));
    }

    /** Arrival ends travel ownership; V2 must establish all live market truth afterward. */
    public static boolean completeArrival(UUID mobId, CommuteDirective.Binding binding) {
        if (mobId == null || binding == null
                || !VillageIntentRegistry.releaseIfCurrent(mobId, binding.intent())) {
            return false;
        }
        VillageRouteAttemptRegistry.recordArrival(mobId, binding.intent().destination());
        return true;
    }

    /** Called only after ExploringGoal's existing terminal physical PATH_FAILURE budget is spent. */
    public static boolean recordTerminalRouteFailure(
            UUID mobId, CommuteDirective.Binding binding, long now) {
        if (mobId == null || binding == null
                || !VillageIntentRegistry.releaseIfCurrent(mobId, binding.intent())) {
            return false;
        }
        VillageRouteAttemptRegistry.recordTerminalFailure(
                mobId, binding.intent().destination(), now);
        return true;
    }

    public static RouteAttemptEvidence routeAttemptEvidence(UUID mobId, long now) {
        return VillageRouteAttemptRegistry.snapshot(mobId, now);
    }

    public static void release(UUID mobId) {
        VillageIntentRegistry.release(mobId);
        VillageRouteAttemptRegistry.release(mobId);
    }

    public static void shutdownServerState() {
        VillageIntentRegistry.shutdownServerState();
        VillageRouteAttemptRegistry.shutdownServerState();
    }

    private static Optional<CommuteDirective> postRevalidationDirective(UUID mobId) {
        return VillageIntentRegistry.current(mobId).map(CommuteDirective::requiredTrade);
    }

    private static ResolvedFacts resolve(
            ServerLevel level, PathfinderMob mob, boolean interrupted) {
        Container backpack = PlayerMobs.backpack(mob);
        Optional<WorkDemandPolicy.MaterialDemand> demand = backpack == null
                ? Optional.empty()
                : WorkDemandPolicy.select(
                                backpack,
                                mob.getMainHandItem(),
                                mob.getOffhandItem(),
                                ScavengerConfig.get())
                        .map(WorkDemandPolicy.WorkDemand::payload);

        ExistingRouteFeasibility.ExistingRouteStatus routeStatus = demand.isEmpty()
                || backpack == null
                ? ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN
                : ExistingRouteFeasibility.status(
                        level,
                        mob.getUUID(),
                        demand.get(),
                        backpack,
                        mob.getMainHandItem(),
                        mob.getOffhandItem(),
                        ScavengerConfig.get());

        VillageMemorySavedData store = VillageMemorySavedData.peekInDimension(level);
        Optional<MobVillageMemory> memory = store == null
                ? Optional.empty()
                : store.peek(mob.getUUID());
        Set<SettlementKey> compatible = memory.stream()
                .flatMap(existing -> existing.villages().stream())
                .map(village -> new SettlementKey(level.dimension(), village.anchor()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<SettlementDestinationFacts> candidates = demand.isEmpty() || backpack == null
                || store == null
                ? List.of()
                : candidateFacts(
                        store,
                        mob.getUUID(),
                        level,
                        demand.get(),
                        backpack,
                        mob.getMainHandItem(),
                        mob.getOffhandItem());

        SettlementOpinionInputs opinion = SettlementOpinionInputs.peek(mob.getUUID());
        return new ResolvedFacts(
                new VillageIntentFacts(demand, routeStatus, compatible, interrupted),
                candidates,
                opinion.scoring(),
                opinion.context());
    }

    private static List<SettlementDestinationFacts> candidateFacts(
            VillageMemorySavedData store,
            UUID mobId,
            ServerLevel level,
            WorkDemandPolicy.MaterialDemand demand,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand) {
        Set<TradeOutputCapability> desired = new LinkedHashSet<>();
        BuiltInRegistries.ITEM.getOptional(demand.materialKey())
                .filter(item -> item != net.minecraft.world.item.Items.AIR)
                .map(ItemStack::new)
                .map(TradeOutputCapability::of)
                .ifPresent(desired::add);
        TradePurchaseProjection.activeSpecFor(
                        demand, backpack, mainHand, offHand, ScavengerConfig.get())
                .map(ScavengerCrafting.ConsumerRecipeSpec::output)
                .map(ItemStack::new)
                .map(TradeOutputCapability::of)
                .ifPresent(desired::add);
        if (desired.isEmpty()) {
            return List.of();
        }

        Map<SettlementKey, SettlementDestinationFacts> merged = new TreeMap<>();
        for (TradeOutputCapability output : desired) {
            for (SettlementDestinationFacts fact : store.rankingFacts(
                    mobId, level.dimension(), output, level.getGameTime())) {
                merged.merge(fact.key(), fact, VillageInteractionDirector::strongerEvidence);
            }
        }
        return List.copyOf(merged.values());
    }

    private static SettlementDestinationFacts strongerEvidence(
            SettlementDestinationFacts left, SettlementDestinationFacts right) {
        if (left.capabilityEvidence() == CapabilityEvidenceClass.POSITIVE_HINT) {
            return left;
        }
        return right.capabilityEvidence() == CapabilityEvidenceClass.POSITIVE_HINT ? right : left;
    }

    record ResolvedFacts(
            VillageIntentFacts intentFacts,
            Collection<SettlementDestinationFacts> candidates,
            DiscretionaryScoringInput opinionInput,
            SettlementOpinionContext opinionContext) {
    }

}
