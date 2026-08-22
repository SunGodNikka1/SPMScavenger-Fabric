package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.village.compost.CompostAdmission;
import com.noobk.spmscavenger.village.compost.CompostDeliveryPlan;
import com.noobk.spmscavenger.village.compost.CompostEpisodeCooldown;
import com.noobk.spmscavenger.village.compost.CompostTargetSelector;
import com.noobk.spmscavenger.village.compost.CompostTerminalOutcome;
import com.noobk.spmscavenger.village.compost.CompostTransaction;
import com.noobk.spmscavenger.village.compost.CompostTuning;
import com.noobk.spmscavenger.PlayerMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * V3-F opportunistic compost episode at priority 5 (task-58).
 */
public final class CompostGoal extends Goal {

    private enum Phase {
        PATHING,
        INTERACT_PREPARE
    }

    private final Mob mob;
    private final GoalSelector selector;
    private final double moveSpeed;

    private Phase phase = Phase.PATHING;
    private int phaseTicks;
    private int scanCooldown;
    private CompostDeliveryPlan plan;
    private Path acceptedPath;
    private boolean committed;
    private CompostTerminalOutcome terminalOutcome = CompostTerminalOutcome.ABORTED;

    public CompostGoal(Mob mob, GoalSelector selector, double moveSpeed) {
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
        if (CompostEpisodeCooldown.isCooling(mob.getUUID(), now)) {
            return false;
        }
        if (!CompostAdmission.mobGriefingPermits(level)) {
            return false;
        }
        if (!CompostAdmission.permits(mob, selector, null)) {
            return false;
        }
        var selection = CompostTargetSelector.select(level, mob, now);
        if (selection.isEmpty()) {
            scanCooldown = CompostTuning.EMPTY_SCAN_COOLDOWN;
            return false;
        }
        plan = selection.get();
        acceptedPath = plan.pathToComposter();
        phase = Phase.PATHING;
        phaseTicks = 0;
        committed = false;
        terminalOutcome = CompostTerminalOutcome.ABORTED;
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
        if (!CompostAdmission.mobGriefingPermits(level)) {
            return false;
        }
        if (!CompostAdmission.permits(mob, selector, this)) {
            return false;
        }
        if (phase == Phase.INTERACT_PREPARE) {
            return true;
        }
        return phase == Phase.PATHING && phaseTicks <= CompostTuning.PATH_TIMEOUT_TICKS;
    }

    @Override
    public void start() {
        phaseTicks = 0;
        if (acceptedPath != null) {
            mob.getNavigation().moveTo(acceptedPath, moveSpeed);
        } else if (plan != null) {
            BlockPos pos = plan.composterPos();
            mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), moveSpeed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (committed && mob.level() instanceof ServerLevel level) {
            CompostEpisodeCooldown.recordOutcome(mob.getUUID(), terminalOutcome, level.getGameTime());
        }
        plan = null;
        acceptedPath = null;
        phase = Phase.PATHING;
        phaseTicks = 0;
        committed = false;
        terminalOutcome = CompostTerminalOutcome.ABORTED;
        scanCooldown = CompostTuning.EMPTY_SCAN_COOLDOWN;
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
            case INTERACT_PREPARE -> tickInteractPrepare(level);
            default -> { }
        }
    }

    private void tickPathing(ServerLevel level) {
        BlockPos pos = plan.composterPos();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (mob.distanceToSqr(Vec3.atCenterOf(pos)) < CompostTuning.REACH_DISTANCE_SQR) {
            mob.getNavigation().stop();
            phase = Phase.INTERACT_PREPARE;
            phaseTicks = 0;
        } else if (phaseTicks > CompostTuning.PATH_TIMEOUT_TICKS) {
            stop();
        }
    }

    private void tickInteractPrepare(ServerLevel level) {
        BlockPos pos = plan.composterPos();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (phaseTicks == 1) {
            if (!CompostAdmission.commitPreflight(level, mob, selector, this, plan, level.getGameTime())) {
                stop();
                return;
            }
        }
        if (phaseTicks < CompostTuning.INTERACT_PREPARE_TICKS) {
            return;
        }
        if (!CompostAdmission.commitPreflight(level, mob, selector, this, plan, level.getGameTime())) {
            stop();
            return;
        }
        var delivery = plan.delivery();
        CompostTransaction.CommitResult result = CompostTransaction.commit(
                level,
                mob,
                PlayerMobs.backpack(mob),
                delivery.slot(),
                pos);
        if (result.outcome() != CompostTransaction.CommitOutcome.COMMITTED) {
            stop();
            return;
        }
        committed = true;
        terminalOutcome = result.levelAfter() > result.levelBefore()
                ? CompostTerminalOutcome.COMMITTED
                : CompostTerminalOutcome.COMMITTED_NO_LEVEL_CHANGE;
        mob.swing(InteractionHand.MAIN_HAND);
        stop();
    }
}
