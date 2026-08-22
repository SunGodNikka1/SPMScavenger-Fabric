package com.noobk.spmscavenger.village.compost;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.activity.MandatoryOwnershipRegistry;
import com.noobk.spmscavenger.mining.MiningProjectSavedData;
import com.noobk.spmscavenger.village.MobVillageMemory;
import com.noobk.spmscavenger.village.PlayerMobVillagePolicySavedData;
import com.noobk.spmscavenger.village.SettlementBoundsPolicy;
import com.noobk.spmscavenger.village.VillageMemorySavedData;
import com.noobk.spmscavenger.village.VillageWorkAdmission;
import com.noobk.spmscavenger.village.work.ComposterWorkFacts;
import com.noobk.spmscavenger.village.work.ComposterWorkFactsService;
import com.noobk.spmscavenger.village.work.FreshnessPolicy;
import com.noobk.spmscavenger.village.work.SettlementIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Admission wiring for {@link com.noobk.spmscavenger.goal.CompostGoal}.
 */
public final class CompostAdmission {

    private CompostAdmission() {}

    public static boolean mobGriefingPermits(ServerLevel level) {
        return level != null && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    public static boolean permits(Mob mob, GoalSelector selector, @Nullable Goal excludeFromObservation) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!mobGriefingPermits(level)) {
            return false;
        }
        long now = level.getGameTime();
        MiningProjectSavedData store = MiningProjectSavedData.get(level);
        ActivityObservationService.Observation observation = excludeFromObservation == null
                ? ActivityObservationService.observe(selector, mob, store, now)
                : ActivityObservationService.observeExcluding(
                        selector, excludeFromObservation, mob, store, now);
        if (concurrentVillageWork(observation)) {
            return false;
        }
        var profile = PlayerMobVillagePolicySavedData.profileOf(level.getServer(), mob.getUUID());
        boolean combat = mob.getTarget() != null;
        return VillageWorkAdmission.evaluate(
                profile,
                observation,
                combat,
                MandatoryOwnershipRegistry.liveClaim(mob.getUUID(), now),
                now).permitted();
    }

    public static boolean settlementStillRemembered(MobVillageMemory memory, SettlementIdentity settlement) {
        if (memory == null || settlement == null) {
            return false;
        }
        return memory.villages().stream()
                .anyMatch(village -> village.anchor().equals(settlement.anchor()))
                || memory.home().map(home -> home.anchor().equals(settlement.anchor())).orElse(false);
    }

    public static boolean currentComposterEvidence(
            MobVillageMemory memory,
            SettlementIdentity settlement,
            Optional<ComposterWorkFacts> peeked,
            long gameTime) {
        if (!settlementStillRemembered(memory, settlement) || peeked.isEmpty()) {
            return false;
        }
        ComposterWorkFacts facts = FreshnessPolicy.apply(peeked.get(), gameTime);
        return facts.isReadable();
    }

    public static boolean withinInteractionDistance(Mob mob, BlockPos composterPos) {
        return mob != null
                && composterPos != null
                && mob.distanceToSqr(Vec3.atCenterOf(composterPos)) < CompostTuning.REACH_DISTANCE_SQR;
    }

    public static boolean commitPreflight(
            ServerLevel level,
            Mob mob,
            GoalSelector selector,
            Goal excludeFromObservation,
            CompostDeliveryPlan plan,
            long gameTime) {
        if (plan == null || !permits(mob, selector, excludeFromObservation)) {
            return false;
        }
        BlockPos composterPos = plan.composterPos();
        if (!withinInteractionDistance(mob, composterPos)) {
            return false;
        }
        if (!level.isLoaded(composterPos)) {
            return false;
        }
        BlockState state = level.getBlockState(composterPos);
        if (!CompostMechanicalEligibility.canAcceptInput(state)) {
            return false;
        }
        if (!SettlementBoundsPolicy.within(composterPos, plan.settlement().anchor())) {
            return false;
        }
        Optional<MobVillageMemory> memory = VillageMemorySavedData.get(level).peek(mob.getUUID());
        if (memory.isEmpty()) {
            return false;
        }
        if (!currentComposterEvidence(
                memory.get(),
                plan.settlement(),
                ComposterWorkFactsService.peek(level, plan.settlement()),
                gameTime)) {
            return false;
        }
        var delivery = plan.delivery();
        return CompostExpendabilityPolicy.slotStillDisposable(
                PlayerMobs.backpack(mob),
                mob.getMainHandItem(),
                mob.getOffhandItem(),
                delivery.slot(),
                delivery.item(),
                ScavengerConfig.get());
    }

    private static boolean concurrentVillageWork(ActivityObservationService.Observation observation) {
        return observation.activeClasses().contains(ActivityClass.VILLAGE_WORK);
    }
}
