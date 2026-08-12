package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.experience.RestSessionCoordinator;
import com.noobk.spmscavenger.opinion.ActivityAdmission;
import com.noobk.spmscavenger.opinion.DiscretionaryAuthority;
import com.noobk.spmscavenger.opinion.IntentLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Puts down a campfire and hangs around it when there is nothing else to do.
 *
 * <h2>Why a campfire and not just "stand somewhere"</h2>
 *
 * Idling is invisible; idling <em>around something</em> is a scene. A campfire gives a group of
 * PlayerMobs a place to converge, which reads as a camp rather than as pathfinding noise — and it
 * emerges rather than being scripted, because several mobs independently choosing the nearest
 * campfire end up in the same spot.
 *
 * <p>It also completes the crafting chain the mod already has. A campfire is 3 logs + 3 sticks +
 * 1 coal — a 3x3 recipe, so it needs the workbench the mob already knows how to build, and it
 * consumes exactly what gathering already produces.
 *
 * <h2>Standing near, never on</h2>
 *
 * A campfire damages anything standing in it. The idle spot is therefore always an <b>adjacent</b>
 * block, never the fire itself, and the fire is only placed where the mob is not already standing.
 *
 * <p>Even so, a mob may still wander onto one — SPM's own {@code FireBucketGoal} sprints it to water
 * when that happens, which is the host mod's existing recovery and not something to duplicate here.
 *
 * <h2>Lowest priority by design</h2>
 *
 * This is what a mob does when it has nothing better to do. It must never outrank shelter, combat,
 * looting or crafting, and it yields the moment any of them wants the mob.
 */
public class CampfireGoal extends Goal {

    private final Mob mob;
    private final double speed;

    private BlockPos firePos;
    private BlockPos idlePos;
    private final PhasedScanClock scanClock;
    private int approachTicks;
    private int placeTicks;
    private boolean restClaimOpened;

    private static final int MAX_APPROACH_TICKS = 200;
    private static final int PLACE_TICKS = 20;

    public CampfireGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.scanClock = new PhasedScanClock(
                mob.getId(),
                RestCampfireFeasibility.SCAN_INTERVAL,
                RestCampfireFeasibility.SCAN_PHASE_SALT);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** GAO-4R — decision-time admission without calling {@link #canUse()}. */
    public ActivityAdmission inspectAdmission(long gameTime) {
        return RestExecutorAdmission.inspect(mob, gameTime, scanClock, firePos, idlePos);
    }

    @Override
    public boolean canUse() {
        if (!ShelterActivityEnvelope.permitsVoluntaryDisplacement(mob)) {
            return false;
        }
        ScavengerConfig cfg = ScavengerConfig.get();
        if (!cfg.enabled || !cfg.campfire) {
            return false;
        }
        if (DiscretionaryAuthority.opinionGatesConsumers()
                && !DiscretionaryAuthority.mayStartDiscretionaryRest(mob.getUUID())) {
            return false;
        }
        if (!scanClock.claim(mob.level().getGameTime())) {
            return false;
        }
        if (mob.getTarget() != null) {
            return false;
        }
        Level level = mob.level();
        firePos = RestCampfireFeasibility.findCampfire(level, mob.blockPosition());

        if (firePos == null) {
            if (!RestCampfireFeasibility.carriesCampfire(mob)) {
                return false;
            }
            return true;
        }
        idlePos = RestCampfireFeasibility.spotBeside(level, firePos);
        if (idlePos == null) {
            return false;
        }
        return mob.blockPosition().distSqr(idlePos) > RestCampfireFeasibility.ARRIVED_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        if (!ShelterActivityEnvelope.permitsVoluntaryDisplacement(mob)) {
            return false;
        }
        if (DiscretionaryAuthority.mustYieldDiscretionaryRest(mob.getUUID())) {
            UUID restIntentId = DiscretionaryAuthority.runningRestIntentId(mob.getUUID());
            if (restIntentId != null) {
                DiscretionaryAuthority.onRestYieldedForExplore(
                        mob.getUUID(), restIntentId, mob.level().getGameTime());
            }
            return false;
        }
        return ScavengerConfig.get().campfire
                && mob.getTarget() == null
                && approachTicks < MAX_APPROACH_TICKS
                && (firePos != null || RestCampfireFeasibility.carriesCampfire(mob));
    }

    @Override
    public void start() {
        approachTicks = 0;
        placeTicks = 0;
        restClaimOpened = false;
        if (DiscretionaryAuthority.opinionGatesConsumers()) {
            DiscretionaryAuthority.onRestAdopted(mob.getUUID(), mob.level().getGameTime());
        }
        if (idlePos != null) {
            mob.getNavigation().moveTo(idlePos.getX() + 0.5, idlePos.getY(), idlePos.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        if (DiscretionaryAuthority.opinionGatesConsumers()) {
            long gameTime = mob.level().getGameTime();
            if (DiscretionaryAuthority.shouldPreserveRestIntentOnCampfireStop(mob.getUUID())) {
                DiscretionaryAuthority.onRestDeliveryComplete(mob.getUUID(), gameTime);
            } else {
                DiscretionaryAuthority.onRestTerminal(
                        mob.getUUID(),
                        IntentLifecycle.INTERRUPTED,
                        gameTime,
                        "campfire-stop");
            }
        }
        firePos = null;
        idlePos = null;
        approachTicks = 0;
        placeTicks = 0;
        restClaimOpened = false;
        scanClock.resetAfter(mob.level().getGameTime());
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Level level = mob.level();

        if (firePos == null) {
            placeCampfire(level);
            return;
        }
        if (idlePos == null) {
            stop();
            return;
        }
        approachTicks++;
        mob.getLookControl().setLookAt(
                firePos.getX() + 0.5, firePos.getY() + 0.5, firePos.getZ() + 0.5);

        if (mob.blockPosition().distSqr(idlePos) <= RestCampfireFeasibility.ARRIVED_SQR) {
            if (!restClaimOpened) {
                RestSessionCoordinator.openCampfireRest(
                        mob, firePos, idlePos, level.getGameTime());
                restClaimOpened = true;
                DiscretionaryAuthority.onRestClaimOpened(mob.getUUID(), level.getGameTime());
            }
            mob.getNavigation().stop();
            return;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(idlePos.getX() + 0.5, idlePos.getY(), idlePos.getZ() + 0.5, speed);
        }
    }

    private void placeCampfire(Level level) {
        if (!RestCampfireFeasibility.canPlaceCampfire(level)) {
            stop();
            return;
        }
        Container backpack = PlayerMobs.backpack(mob);
        if (backpack == null) {
            stop();
            return;
        }
        BlockPos spot = RestCampfireFeasibility.spotBeside(level, mob.blockPosition());
        if (spot == null) {
            stop();
            return;
        }
        if (++placeTicks < PLACE_TICKS) {
            return;
        }
        placeTicks = 0;

        for (int i = 0; i < backpack.getContainerSize(); i++) {
            if (backpack.getItem(i).is(Items.CAMPFIRE)) {
                backpack.removeItem(i, 1);
                level.setBlock(spot, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
                mob.swing(InteractionHand.MAIN_HAND);
                firePos = spot;
                idlePos = RestCampfireFeasibility.spotBeside(level, spot);
                return;
            }
        }
        stop();
    }
}
