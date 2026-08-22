package com.noobk.spmscavenger.village.compost;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.work.ComposterWorkFacts;
import com.noobk.spmscavenger.village.work.ComposterWorkFactsService;
import com.noobk.spmscavenger.village.work.FreshnessPolicy;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic composter ranking from cached facts + mob position (task-58).
 */
public final class CompostTargetSelector {

    /**
     * Cheap eligibility before any path probe — loaded, settlement-bound, mechanical input capacity.
     */
    @FunctionalInterface
    public interface CheapComposterFilter {
        boolean admits(BlockPos pos, BlockPos anchor);
    }

    @FunctionalInterface
    interface PathProbe {
        Path probe(BlockPos pos);
    }

    private CompostTargetSelector() {}

    public static Optional<CompostDeliveryPlan> select(ServerLevel level, Mob mob, long gameTime) {
        if (level == null || mob == null) {
            return Optional.empty();
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return Optional.empty();
        }
        ComposterWorkFactsService.scheduleForMob(level, mob.getUUID());

        Container backpack = PlayerMobs.backpack(mob);
        ItemStack mainHand = mob.getMainHandItem();
        ItemStack offHand = mob.getOffhandItem();
        ScavengerConfig cfg = ScavengerConfig.get();
        Optional<CompostExpendabilityPolicy.InsertionOffer> delivery =
                CompostExpendabilityPolicy.planInsertion(backpack, mainHand, offHand, cfg);
        if (delivery.isEmpty()) {
            return Optional.empty();
        }

        List<SettlementCandidate> settlements = candidateSettlements(level, memory.get(), gameTime);
        for (SettlementCandidate candidate : settlements) {
            Optional<CompostDeliveryPlan> plan = selectInSettlement(
                    level, mob, candidate.identity(), candidate.facts(), delivery.get());
            if (plan.isPresent()) {
                return plan;
            }
        }
        return Optional.empty();
    }

    private record SettlementCandidate(SettlementIdentity identity, ComposterWorkFacts facts) {}

    private static List<SettlementCandidate> candidateSettlements(
            ServerLevel level, MobVillageMemory memory, long gameTime) {
        List<SettlementCandidate> out = new ArrayList<>();
        memory.home().ifPresent(home -> addIfReadable(level, home.anchor(), out, gameTime));
        for (KnownVillage village : memory.villages()) {
            if (memory.home().map(h -> h.anchor().equals(village.anchor())).orElse(false)) {
                continue;
            }
            addIfReadable(level, village.anchor(), out, gameTime);
        }
        return out;
    }

    private static void addIfReadable(
            ServerLevel level,
            BlockPos anchor,
            List<SettlementCandidate> out,
            long gameTime) {
        SettlementIdentity identity = SettlementIdentity.of(level.dimension(), anchor);
        ComposterWorkFactsService.peek(level, identity)
                .map(facts -> FreshnessPolicy.apply(facts, gameTime))
                .filter(ComposterWorkFacts::isReadable)
                .ifPresent(facts -> out.add(new SettlementCandidate(identity, facts)));
    }

    /**
     * Rank eligible composters by distance then stable {@link BlockPos} order, then cap probe budget.
     * Raw fact list order must not determine which positions receive path probes (CLOSE-58-2).
     */
    static List<BlockPos> rankedProbeOrder(
            List<BlockPos> rawPositions,
            Vec3 mobPosition,
            BlockPos anchor,
            CheapComposterFilter filter) {
        List<RankedPosition> eligible = new ArrayList<>();
        for (BlockPos pos : rawPositions) {
            if (!filter.admits(pos, anchor)) {
                continue;
            }
            eligible.add(new RankedPosition(pos, mobPosition.distanceToSqr(Vec3.atCenterOf(pos))));
        }
        eligible.sort(Comparator.comparingDouble(RankedPosition::distanceSq)
                .thenComparing(candidate -> candidate.pos().toShortString()));
        return eligible.stream()
                .limit(CompostTuning.MAX_COMPOSTER_CANDIDATES)
                .map(RankedPosition::pos)
                .toList();
    }

    static Optional<BlockPos> selectReachableComposter(
            List<BlockPos> probeOrder,
            PathProbe pathProbe) {
        int probes = 0;
        for (BlockPos pos : probeOrder) {
            probes++;
            if (probes > CompostTuning.MAX_COMPOSTER_CANDIDATES) {
                break;
            }
            Path path = pathProbe.probe(pos);
            if (path != null && path.canReach()) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    static int countPathProbes(List<BlockPos> probeOrder, PathProbe pathProbe) {
        int probes = 0;
        for (BlockPos pos : probeOrder) {
            if (probes >= CompostTuning.MAX_COMPOSTER_CANDIDATES) {
                break;
            }
            probes++;
            pathProbe.probe(pos);
        }
        return probes;
    }

    static Optional<CompostDeliveryPlan> selectInSettlement(
            ServerLevel level,
            Mob mob,
            SettlementIdentity identity,
            ComposterWorkFacts facts,
            CompostExpendabilityPolicy.InsertionOffer delivery) {
        BlockPos anchor = identity.anchor();
        Vec3 mobPosition = mob.position();
        List<BlockPos> probeOrder = rankedProbeOrder(
                facts.composterPositions(),
                mobPosition,
                anchor,
                (pos, settlementAnchor) -> level.isLoaded(pos)
                        && SettlementBoundsPolicy.within(pos, settlementAnchor)
                        && CompostMechanicalEligibility.canAcceptInput(level.getBlockState(pos)));

        Optional<BlockPos> chosen = selectReachableComposter(
                probeOrder,
                pos -> pathToComposter(mob, pos));
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        BlockPos composterPos = chosen.get();
        Path path = pathToComposter(mob, composterPos);
        if (path == null || !path.canReach()) {
            return Optional.empty();
        }
        return Optional.of(new CompostDeliveryPlan(
                identity, facts, composterPos, path, delivery));
    }

    private static Path pathToComposter(Mob mob, BlockPos pos) {
        PathNavigation navigation = mob.getNavigation();
        return navigation.createPath(pos.getX(), pos.getY(), pos.getZ(), 2);
    }

    private record RankedPosition(BlockPos pos, double distanceSq) {}
}
