package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Places a torch when it is dark enough to matter and the mob is carrying one.
 *
 * <h2>Why light level and not time of day</h2>
 *
 * The threshold defaults to <b>7</b> — the vanilla block-light level below which hostile mobs
 * spawn. That makes the behaviour self-limiting in a way a timer never would: a mob lights a spot
 * exactly until it is no longer a spawn point, then stops, because its own condition stopped being
 * true. It also means the mob lights caves in daylight and leaves a torch-lit room alone at
 * midnight, which is what a player does.
 *
 * <h2>Placement is checked, never forced</h2>
 *
 * Every candidate goes through {@code canSurvive}. A torch needs support, and {@code setBlock} does
 * not check for it — an unsupported torch would pop off as an item on the next neighbour update,
 * which reads as the mob throwing torches on the floor. Standing torches only: a wall torch needs a
 * facing chosen against a solid side, and getting that wrong is the same bug in a different shape.
 *
 * <h2>The mob pays for it</h2>
 *
 * The torch comes out of the backpack. This is deliberate — {@code /playermob order … place} conjures
 * blocks from air, and copying that here would make torches free and infinite. A scavenger that runs
 * out has to go and find more, which is the entire point of the gathering goal.
 */
public class PlaceTorchGoal extends Goal {

    private final Mob mob;
    private final double speed;

    private BlockPos target;
    private int cooldown;
    private int approachTicks;
    private final PhasedScanClock scanClock;

    private static final int SCAN_INTERVAL = 20;
    private static final int SCAN_PHASE_SALT = 23;
    /** Give up walking to a spot after this long; something is in the way. */
    private static final int MAX_APPROACH_TICKS = 100;
    private static final double REACH_SQR = 4.0;

    public PlaceTorchGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.scanClock = new PhasedScanClock(mob.getId(), SCAN_INTERVAL, SCAN_PHASE_SALT);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.placeTorches) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!scanClock.claim(mob.level().getGameTime())) {
            return false;
        }
        if (torchSlot() < 0) {
            return false;
        }
        // Cheap test first: if it is already bright here, there is nothing to do and no scan to run.
        Level level = mob.level();
        if (level.getBrightness(LightLayer.BLOCK, mob.blockPosition()) >= cfg.torchLightLevel) {
            return false;
        }
        target = findSpot(cfg);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && approachTicks < MAX_APPROACH_TICKS && torchSlot() >= 0;
    }

    @Override
    public void start() {
        approachTicks = 0;
        if (target != null) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        target = null;
        approachTicks = 0;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        approachTicks++;
        mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (mob.blockPosition().distSqr(target) > REACH_SQR) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
            }
            return;
        }
        place();
    }

    private void place() {
        Level level = mob.level();
        BlockState torch = Blocks.TORCH.defaultBlockState();

        // Re-check on arrival: the world may have changed during the walk, and another mob may have
        // lit this very spot while this one was on its way.
        if (level.getBlockState(target).canBeReplaced() && torch.canSurvive(level, target)) {
            int slot = torchSlot();
            if (slot >= 0) {
                Container backpack = PlayerMobs.backpack(mob);
                if (backpack != null) {
                    backpack.removeItem(slot, 1);
                    level.setBlock(target, torch, Block.UPDATE_ALL);
                    mob.swing(InteractionHand.MAIN_HAND);
                }
            }
        }
        cooldown = ScavengerConfig.get().torchCooldownTicks;
        target = null;
    }

    /** Backpack slot holding a torch, or -1. */
    private int torchSlot() {
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            return -1;
        }
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.is(Items.TORCH)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The nearest dark, supported, replaceable spot. Scans a flattened box — wide but only a couple
     * of blocks tall — because a torch three blocks overhead lights the same room and cannot be
     * walked to.
     */
    private BlockPos findSpot(ScavengerConfig cfg) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        int r = (int) cfg.torchSearchRadius;
        BlockState torch = Blocks.TORCH.defaultBlockState();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBrightness(LightLayer.BLOCK, pos) >= cfg.torchLightLevel) {
                        continue;
                    }
                    if (!level.getBlockState(pos).canBeReplaced() || !torch.canSurvive(level, pos)) {
                        continue;
                    }
                    double dist = pos.distSqr(origin);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }
}
