package com.noobk.spmscavenger.village.population;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.VillagePerception;
import com.noobk.spmscavenger.village.work.FreshnessPolicy;
import com.noobk.spmscavenger.village.work.PopulationSupportVacancyPolicy;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import com.noobk.spmscavenger.village.work.VillageWorkFacts;
import com.noobk.spmscavenger.village.work.VillageWorkFactsService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Deterministic adult villager ranking for population food (task-57 §3).
 */
public final class PopulationFoodRecipientSelector {

    /**
     * Deterministic adult-villager enumeration; at most
     * {@link PopulationFoodTuning#MAX_RECIPIENT_CANDIDATES} nearest adults are inspected.
     */
    @FunctionalInterface
    public interface VillagerRecipientCandidateSource {
        boolean enumerate(Predicate<Villager> visitor);
    }

    private PopulationFoodRecipientSelector() {}

    public static Optional<PopulationFoodDeliveryPlan> select(ServerLevel level, Mob mob, long gameTime) {
        if (level == null || mob == null) {
            return Optional.empty();
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return Optional.empty();
        }
        VillageWorkFactsService.scheduleForMob(level, mob.getUUID());

        Container backpack = PlayerMobs.backpack(mob);
        ItemStack mainHand = mob.getMainHandItem();
        ItemStack offHand = mob.getOffhandItem();

        List<SettlementCandidate> settlements = candidateSettlements(level, memory.get(), gameTime);
        for (SettlementCandidate candidate : settlements) {
            Optional<PopulationFoodDeliveryPlan> plan = selectInSettlement(
                    level,
                    mob,
                    candidate.identity(),
                    candidate.facts(),
                    backpack,
                    mainHand,
                    offHand,
                    gameTime,
                    villagerSource(level, mob, candidate.identity().anchor()));
            if (plan.isPresent()) {
                return plan;
            }
        }
        return Optional.empty();
    }

    private record SettlementCandidate(SettlementIdentity identity, VillageWorkFacts facts) {}

    private static List<SettlementCandidate> candidateSettlements(
            ServerLevel level, MobVillageMemory memory, long gameTime) {
        List<SettlementCandidate> out = new ArrayList<>();
        memory.home().ifPresent(home -> addIfCandidate(level, home.anchor(), out, gameTime));
        for (KnownVillage village : memory.villages()) {
            if (memory.home().map(h -> h.anchor().equals(village.anchor())).orElse(false)) {
                continue;
            }
            addIfCandidate(level, village.anchor(), out, gameTime);
        }
        return out;
    }

    private static void addIfCandidate(
            ServerLevel level,
            BlockPos anchor,
            List<SettlementCandidate> out,
            long gameTime) {
        SettlementIdentity identity = SettlementIdentity.of(level.dimension(), anchor);
        VillageWorkFactsService.peek(level, identity)
                .map(facts -> FreshnessPolicy.apply(facts, gameTime))
                .filter(PopulationSupportVacancyPolicy::isPopulationSupportCandidate)
                .ifPresent(facts -> out.add(new SettlementCandidate(identity, facts)));
    }

    static Optional<PopulationFoodDeliveryPlan> selectInSettlement(
            ServerLevel level,
            Mob mob,
            SettlementIdentity identity,
            VillageWorkFacts facts,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            long gameTime,
            VillagerRecipientCandidateSource villagers) {
        BlockPos anchor = identity.anchor();
        List<VillagerCandidate> candidates = new ArrayList<>();
        int[] expensiveProbes = {0};
        boolean enumerationComplete = villagers.enumerate(villager -> {
            if (expensiveProbes[0] >= PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES) {
                return false;
            }
            if (!needsFood(villager)) {
                return true;
            }
            if (PopulationFoodInterlocks.blocksHandoff(mob.getUUID(), villager.getUUID(), gameTime)) {
                return true;
            }
            expensiveProbes[0]++;
            if (!BreederLocalHomeProof.hasReachableVacantHome(level, villager)) {
                return true;
            }
            Path path = pathToRecipient(mob, villager);
            if (path == null || !path.canReach()) {
                return true;
            }
            candidates.add(new VillagerCandidate(villager, path));
            return true;
        });
        if (candidates.isEmpty() && !enumerationComplete) {
            return Optional.empty();
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        VillagerCandidate best = candidates.stream()
                .min(Comparator.<VillagerCandidate>comparingDouble(c -> mob.distanceToSqr(c.villager()))
                        .thenComparing(c -> c.villager().getUUID().toString()))
                .orElseThrow();
        Optional<PopulationFoodExpendabilityPolicy.DeliveryOffer> delivery =
                PopulationFoodExpendabilityPolicy.planDelivery(
                        backpack,
                        mainHand,
                        offHand,
                        VillagerFoodInventory.inventoryFoodPoints(best.villager()));
        if (delivery.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PopulationFoodDeliveryPlan(
                identity,
                facts,
                best.villager().getUUID(),
                best.villager(),
                best.path(),
                delivery.get()));
    }

    static VillagerRecipientCandidateSource villagerSource(
            ServerLevel level, Mob mob, BlockPos anchor) {
        return visitor -> {
            double radius = VillagePerception.VILLAGE_QUERY_RADIUS;
            AABB box = mob.getBoundingBox().inflate(radius, radius, radius);
            List<Villager> matches = new ArrayList<>();
            level.getEntities(
                    EntityType.VILLAGER,
                    box,
                    villager -> isEligibleAdult(villager, anchor),
                    matches,
                    PopulationFoodTuning.MAX_RECIPIENT_CANDIDATES);
            matches.sort(Comparator.comparingDouble((Villager villager) -> mob.distanceToSqr(villager))
                    .thenComparing(villager -> villager.getUUID().toString()));
            for (Villager villager : matches) {
                if (!visitor.test(villager)) {
                    return false;
                }
            }
            return true;
        };
    }

    public static boolean isEligibleAdult(Villager villager, BlockPos anchor) {
        return villager != null
                && villager.isAlive()
                && !villager.isRemoved()
                && villager.getAge() == 0
                && SettlementBoundsPolicy.within(villager.blockPosition(), anchor);
    }

    public static boolean needsFood(Villager villager) {
        return villager != null && villager.wantsMoreFood() && !villager.canBreed();
    }

    private static Path pathToRecipient(Mob mob, Villager villager) {
        PathNavigation navigation = mob.getNavigation();
        return navigation.createPath(villager, 2);
    }

    private record VillagerCandidate(Villager villager, Path path) {}
}
