package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.PopulationFoodSupportAdmission;
import com.noobk.spmscavenger.village.population.PopulationFoodDeliveryPlan;
import com.noobk.spmscavenger.village.population.PopulationFoodEpisodeCooldown;
import com.noobk.spmscavenger.village.population.PopulationFoodHandoff;
import com.noobk.spmscavenger.village.population.PopulationFoodRecipientSelector;
import com.noobk.spmscavenger.village.population.PopulationFoodTerminalOutcome;
import com.noobk.spmscavenger.village.population.PopulationFoodTuning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * V3-E population food support episode at priority 4 (task-57).
 */
public final class PopulationFoodSupportGoal extends Goal {

    private enum Phase {
        PATHING,
        HANDOFF_PREPARE,
        ACK_WAIT
    }

    private final Mob mob;
    private final GoalSelector selector;
    private final double moveSpeed;

    private Phase phase = Phase.PATHING;
    private int phaseTicks;
    private int scanCooldown;
    private PopulationFoodDeliveryPlan plan;
    private Path acceptedPath;
    private int committedFoodValue;
    private int recipientFoodPointsBefore;
    private boolean committed;

    public PopulationFoodSupportGoal(Mob mob, GoalSelector selector, double moveSpeed) {
        this.mob = mob;
        this.selector = selector;
        this.moveSpeed = moveSpeed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (PopulationFoodEpisodeCooldown.isCooling(mob.getUUID(), now)) {
            return false;
        }
        if (!PopulationFoodSupportAdmission.mobGriefingPermits(level)) {
            return false;
        }
        if (!PopulationFoodSupportAdmission.permits(mob, selector, null)) {
            return false;
        }
        var selection = PopulationFoodRecipientSelector.select(level, mob, now);
        if (selection.isEmpty()) {
            scanCooldown = PopulationFoodTuning.EMPTY_SCAN_COOLDOWN;
            return false;
        }
        plan = selection.get();
        acceptedPath = plan.pathToRecipient();
        phase = Phase.PATHING;
        phaseTicks = 0;
        committed = false;
        committedFoodValue = 0;
        recipientFoodPointsBefore = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (plan == null) {
            return false;
        }
        if (!mob.isAlive() || mob.isDeadOrDying() || mob.getTarget() != null) {
            return false;
        }
        if (!PopulationFoodSupportAdmission.mobGriefingPermits(level)) {
            return false;
        }
        if (!PopulationFoodSupportAdmission.permits(mob, selector, this)) {
            return false;
        }
        Villager recipient = plan.recipient();
        if (!recipient.isAlive() || recipient.isRemoved()) {
            return false;
        }
        if (phase == Phase.ACK_WAIT || phase == Phase.HANDOFF_PREPARE) {
            return true;
        }
        return phase == Phase.PATHING && phaseTicks <= PopulationFoodTuning.PATH_TIMEOUT_TICKS;
    }

    @Override
    public void start() {
        phaseTicks = 0;
        if (acceptedPath != null) {
            mob.getNavigation().moveTo(acceptedPath, moveSpeed);
        } else if (plan != null) {
            Villager recipient = plan.recipient();
            mob.getNavigation().moveTo(recipient, moveSpeed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (committed && committedFoodValue > 0) {
            PopulationFoodTerminalOutcome outcome = PopulationFoodHandoff.observeDeliveryAck(
                    plan.recipient(), recipientFoodPointsBefore, committedFoodValue)
                    ? PopulationFoodTerminalOutcome.DELIVERED_ACK
                    : PopulationFoodTerminalOutcome.COMMITTED_UNCONFIRMED;
            if (mob.level() instanceof ServerLevel level) {
                PopulationFoodEpisodeCooldown.recordOutcome(
                        mob.getUUID(), outcome, level.getGameTime());
            }
        }
        plan = null;
        acceptedPath = null;
        phase = Phase.PATHING;
        phaseTicks = 0;
        committed = false;
        committedFoodValue = 0;
        recipientFoodPointsBefore = 0;
        scanCooldown = PopulationFoodTuning.EMPTY_SCAN_COOLDOWN;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (plan == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case PATHING -> tickPathing(level);
            case HANDOFF_PREPARE -> tickHandoffPrepare(level);
            case ACK_WAIT -> tickAckWait(level);
            default -> { }
        }
    }

    private void tickPathing(ServerLevel level) {
        Villager recipient = plan.recipient();
        mob.getLookControl().setLookAt(recipient, 30f, 30f);
        double distSq = mob.distanceToSqr(recipient);
        if (distSq < PopulationFoodTuning.REACH_DISTANCE_SQR) {
            mob.getNavigation().stop();
            phase = Phase.HANDOFF_PREPARE;
            phaseTicks = 0;
        } else if (phaseTicks > PopulationFoodTuning.PATH_TIMEOUT_TICKS) {
            stop();
        }
    }

    private void tickHandoffPrepare(ServerLevel level) {
        Villager recipient = plan.recipient();
        mob.getLookControl().setLookAt(recipient, 30f, 30f);
        if (phaseTicks == 1) {
            if (!PopulationFoodSupportAdmission.handoffPreflight(
                    level, mob, selector, this, plan, level.getGameTime())) {
                stop();
                return;
            }
        }
        if (phaseTicks < PopulationFoodTuning.HANDOFF_PREPARE_TICKS) {
            return;
        }
        if (!PopulationFoodSupportAdmission.handoffPreflight(
                level, mob, selector, this, plan, level.getGameTime())) {
            stop();
            return;
        }
        var delivery = plan.delivery();
        PopulationFoodHandoff.CommitResult result = PopulationFoodHandoff.commitKernel(
                level,
                mob,
                recipient,
                PlayerMobs.backpack(mob),
                delivery.item(),
                delivery.count(),
                PopulationFoodSupportAdmission.mobGriefingPermits(level));
        if (result.outcome() != PopulationFoodHandoff.CommitOutcome.COMMITTED) {
            stop();
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        committed = true;
        committedFoodValue = result.villagerFoodValue();
        recipientFoodPointsBefore = result.recipientFoodPointsBefore();
        phase = Phase.ACK_WAIT;
        phaseTicks = 0;
    }

    private void tickAckWait(ServerLevel level) {
        if (phaseTicks >= PopulationFoodTuning.ACK_WAIT_TICKS) {
            stop();
        }
    }
}
