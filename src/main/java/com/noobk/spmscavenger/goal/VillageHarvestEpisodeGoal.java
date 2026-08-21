package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.village.crop.CropHarvestTransaction;
import com.noobk.spmscavenger.village.crop.CropReplantSemantics;
import com.noobk.spmscavenger.village.crop.HarvestCandidatePolicy;
import com.noobk.spmscavenger.village.crop.ManagedCropDomainPolicy;
import com.noobk.spmscavenger.village.VillageHarvestAdmission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * V3-C committed harvest→replant episode at priority 4 (D-VR-079).
 */
public final class VillageHarvestEpisodeGoal extends Goal {

    private static final int HARVEST_WINDUP_TICKS = 10;
    private static final int PATH_TIMEOUT_TICKS = 100;
    private static final int EMPTY_SCAN_COOLDOWN = 40;
    private static final int POST_VISIT_COOLDOWN = 20;
    private static final int SCAN_RADIUS = 8;
    private static final double REACH_DISTANCE_SQR = 4.0;

    private enum Phase {
        IDLE, PATHING, WINDUP
    }

    private final Mob mob;
    private final GoalSelector selector;
    private final double moveSpeed;

    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int scanCooldown;
    private BlockPos targetPos;
    private BlockState committedMatureState;

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
        if (!VillageHarvestAdmission.permits(mob, selector, false)) {
            return false;
        }
        Container backpack = PlayerMobs.backpack(mob);
        BlockPos found = findClosestHarvestCandidate(level, backpack);
        if (found == null) {
            scanCooldown = EMPTY_SCAN_COOLDOWN;
            return false;
        }
        targetPos = found;
        committedMatureState = level.getBlockState(found);
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
        if (!VillageHarvestAdmission.permits(mob, selector, true)) {
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
        mob.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                moveSpeed);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetPos = null;
        committedMatureState = null;
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
        boolean admission = VillageHarvestAdmission.permits(mob, selector, true);
        CropHarvestTransaction.CommitResult result = CropHarvestTransaction.commit(
                level,
                mob,
                PlayerMobs.backpack(mob),
                targetPos,
                committedMatureState,
                admission);
        stop();
    }

    private BlockPos findClosestHarvestCandidate(ServerLevel level, Container backpack) {
        BlockPos mobPos = mob.blockPosition();
        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(mobPos.getX() + dx, mobPos.getY() + dy, mobPos.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    boolean managed = ManagedCropDomainPolicy.isManagedCell(mob, level, cursor);
                    BlockState state = level.getBlockState(cursor);
                    if (!HarvestCandidatePolicy.isHarvestCandidate(managed, state, backpack)) {
                        continue;
                    }
                    double distSq = mobPos.distSqr(cursor);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = cursor.immutable();
                    }
                }
            }
        }
        return closest;
    }
}
