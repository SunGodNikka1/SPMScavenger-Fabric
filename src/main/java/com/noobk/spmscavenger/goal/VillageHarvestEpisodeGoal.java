package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.VillageHarvestAdmission;
import com.noobk.spmscavenger.village.crop.CropHarvestTransaction;
import com.noobk.spmscavenger.village.crop.HarvestCandidatePolicy;
import com.noobk.spmscavenger.village.crop.HarvestCropTargetSelector;
import com.noobk.spmscavenger.village.crop.HarvestCropTargetSelector.HarvestTarget;
import com.noobk.spmscavenger.village.crop.HarvestTargetBackoff;
import com.noobk.spmscavenger.village.crop.ManagedCropDomainContext;
import com.noobk.spmscavenger.village.crop.ManagedCropDomainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * V3-C committed harvest→replant episode at priority 4 (D-VR-079).
 */
public final class VillageHarvestEpisodeGoal extends Goal {

    private static final int HARVEST_WINDUP_TICKS = 10;
    private static final int PATH_TIMEOUT_TICKS = 100;
    private static final int EMPTY_SCAN_COOLDOWN = 40;
    private static final int POST_VISIT_COOLDOWN = 20;
    private static final double REACH_DISTANCE_SQR = 4.0;

    private enum Phase {
        IDLE, PATHING, WINDUP
    }

    private final Mob mob;
    private final GoalSelector selector;
    private final double moveSpeed;
    private final HarvestTargetBackoff targetBackoff = new HarvestTargetBackoff();

    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int scanCooldown;
    private BlockPos targetPos;
    private BlockState committedMatureState;
    private Path acceptedPath;

    public VillageHarvestEpisodeGoal(Mob mob, GoalSelector selector, double moveSpeed) {
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
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        if (mob.getTarget() != null) {
            return false;
        }
        if (!VillageHarvestAdmission.permits(mob, selector, null)) {
            return false;
        }
        Container backpack = PlayerMobs.backpack(mob);
        ManagedCropDomainContext domain = ManagedCropDomainContext.capture(mob, level);
        HarvestCropTargetSelector.SelectionResult selection = HarvestCropTargetSelector.select(
                mob,
                level,
                backpack,
                domain,
                targetBackoff,
                level.getGameTime());
        HarvestTarget target = selection.target();
        if (target == null) {
            scanCooldown = EMPTY_SCAN_COOLDOWN;
            return false;
        }
        targetPos = target.cropPos();
        committedMatureState = target.matureState();
        acceptedPath = target.path();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (phase == Phase.IDLE || targetPos == null || committedMatureState == null) {
            return false;
        }
        if (!mob.isAlive() || mob.isDeadOrDying() || mob.getTarget() != null) {
            return false;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        if (!VillageHarvestAdmission.permits(mob, selector, this)) {
            return false;
        }
        Container backpack = PlayerMobs.backpack(mob);
        boolean managed = ManagedCropDomainPolicy.isManagedCell(mob, level, targetPos);
        return HarvestCandidatePolicy.isHarvestCandidate(
                managed,
                level.getBlockState(targetPos),
                backpack);
    }

    @Override
    public void start() {
        phase = Phase.PATHING;
        phaseTicks = 0;
        if (acceptedPath != null) {
            mob.getNavigation().moveTo(acceptedPath, moveSpeed);
        } else {
            mob.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    moveSpeed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetPos = null;
        committedMatureState = null;
        acceptedPath = null;
        phase = Phase.IDLE;
        phaseTicks = 0;
        scanCooldown = POST_VISIT_COOLDOWN;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (targetPos == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case PATHING -> tickPathing(level);
            case WINDUP -> tickWindup(level);
            default -> { }
        }
    }

    private void tickPathing(ServerLevel level) {
        double dx = (targetPos.getX() + 0.5) - mob.getX();
        double dy = (targetPos.getY() + 0.5) - mob.getY();
        double dz = (targetPos.getZ() + 0.5) - mob.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < REACH_DISTANCE_SQR) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5);
            phase = Phase.WINDUP;
            phaseTicks = 0;
        } else if (phaseTicks > PATH_TIMEOUT_TICKS) {
            targetBackoff.recordFailure(targetPos, level.getGameTime());
            stop();
        }
    }

    private void tickWindup(ServerLevel level) {
        if (phaseTicks == 1) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (phaseTicks < HARVEST_WINDUP_TICKS) {
            return;
        }
        boolean admission = VillageHarvestAdmission.permits(mob, selector, this);
        CropHarvestTransaction.commit(
                level,
                mob,
                PlayerMobs.backpack(mob),
                targetPos,
                committedMatureState,
                admission);
        stop();
    }
}
