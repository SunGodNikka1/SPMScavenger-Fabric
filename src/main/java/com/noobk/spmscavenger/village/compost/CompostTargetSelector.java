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
import net.minecraft.world.level.block.state.BlockState;
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

    static Optional<CompostDeliveryPlan> selectInSettlement(
            ServerLevel level,
            Mob mob,
            SettlementIdentity identity,
            ComposterWorkFacts facts,
            CompostExpendabilityPolicy.InsertionOffer delivery) {
        List<ComposterCandidate> candidates = new ArrayList<>();
        int[] probes = {0};
        for (BlockPos pos : facts.composterPositions()) {
            if (probes[0] >= CompostTuning.MAX_COMPOSTER_CANDIDATES) {
                break;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!SettlementBoundsPolicy.within(pos, identity.anchor())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!CompostMechanicalEligibility.canAcceptInput(state)) {
                continue;
            }
            probes[0]++;
            Path path = pathToComposter(mob, pos);
            if (path == null || !path.canReach()) {
                continue;
            }
            candidates.add(new ComposterCandidate(pos, path, mob.distanceToSqr(Vec3.atCenterOf(pos))));
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        ComposterCandidate best = candidates.stream()
                .min(Comparator.comparingDouble(ComposterCandidate::distanceSq)
                        .thenComparing(candidate -> candidate.pos().toShortString()))
                .orElseThrow();
        return Optional.of(new CompostDeliveryPlan(
                identity, facts, best.pos(), best.path(), delivery));
    }

    private static Path pathToComposter(Mob mob, BlockPos pos) {
        PathNavigation navigation = mob.getNavigation();
        return navigation.createPath(pos.getX(), pos.getY(), pos.getZ(), 2);
    }

    private record ComposterCandidate(BlockPos pos, Path path, double distanceSq) {}
}
