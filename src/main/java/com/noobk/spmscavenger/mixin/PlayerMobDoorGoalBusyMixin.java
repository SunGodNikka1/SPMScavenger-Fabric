package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.goal.DoorPassagePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional SPM v0.86 door-passage lifecycle repair; never owns or operates a door itself.
 *
 * <p>The host still owns vanilla door discovery, the deliberate OPEN/CLOSE animation, and the
 * entity's actual door mutation. This Mixin only rejects semantically empty admissions, pauses the
 * crossing clock during the host's movement-stopping animation, and prevents timeout-before-pass
 * from scheduling a close in front of the mob.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.PlayerMobDoorGoal")
public abstract class PlayerMobDoorGoalBusyMixin extends DoorInteractGoal {

    protected PlayerMobDoorGoalBusyMixin(Mob mob) {
        super(mob);
    }

    @Unique
    private static Method spmscavenger$isOperatingDoor;

    @Unique
    private static Method spmscavenger$isRecovering;

    @Unique
    private static Field spmscavenger$playerMobField;

    @Unique
    private static boolean spmscavenger$resolved;

    @Unique
    private static boolean spmscavenger$warned;

    @Unique
    private BlockPos spmscavenger$episodeDoor;

    @Unique
    private float spmscavenger$startDoorDx;

    @Unique
    private float spmscavenger$startDoorDz;

    @Unique
    private boolean spmscavenger$crossed;

    @Unique
    private BlockPos spmscavenger$lastEpisodeDoor;

    @Unique
    private Path spmscavenger$lastEpisodePath;

    @Unique
    private boolean spmscavenger$lastDoorOpen;

    @Unique
    private boolean spmscavenger$lastEpisodeCrossed;

    /**
     * SPM's flagless passage Goal must not report a successful start while the entity will reject
     * its OPEN request as busy. Once the current operation ends, the host's normal canUse path runs.
     */
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$rejectBusyDoorRequest(CallbackInfoReturnable<Boolean> cir) {
        if (spmscavenger$isBusy(this)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Vanilla may latch a wooden door that is already open. Starting SPM's deliberate OPEN in that
     * state produces a ten-tick no-op animation and stops navigation. The same unchanged door/path
     * encounter is also held after an aborted episode; a different path or door may try normally.
     */
    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true, require = 0)
    private void spmscavenger$rejectEmptyOrDuplicateEpisode(
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !this.hasDoor) {
            return;
        }
        boolean alreadyOpen = this.isOpen();
        Path currentPath = this.mob.getNavigation().getPath();
        boolean sameDoorAndPath = this.doorPos.equals(spmscavenger$lastEpisodeDoor)
                && currentPath == spmscavenger$lastEpisodePath;
        boolean unchangedEncounter = DoorPassagePolicy.unchangedEncounter(
                sameDoorAndPath,
                spmscavenger$lastEpisodeCrossed,
                alreadyOpen != spmscavenger$lastDoorOpen);
        if (!DoorPassagePolicy.admitOpenEpisode(alreadyOpen, unchangedEncounter)) {
            cir.setReturnValue(false);
        }
    }

    /** Capture one immutable passage identity after the host has latched its door. */
    @Inject(method = "start", at = @At("TAIL"), require = 0)
    private void spmscavenger$beginPassageEpisode(CallbackInfo ci) {
        if (!this.hasDoor) {
            spmscavenger$episodeDoor = null;
            return;
        }
        spmscavenger$episodeDoor = this.doorPos.immutable();
        spmscavenger$startDoorDx = (float) (this.doorPos.getX() + 0.5D - this.mob.getX());
        spmscavenger$startDoorDz = (float) (this.doorPos.getZ() + 0.5D - this.mob.getZ());
        spmscavenger$crossed = false;
    }

    /** Door-operation animation owns MOVE, so no physical crossing time may be charged to it. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$pauseCrossingClockDuringOperation(CallbackInfo ci) {
        if (spmscavenger$isOperating(this)) {
            ci.cancel();
        }
    }

    /** Mirror vanilla's door-plane test after the host tick; this is evidence for close-behind. */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void spmscavenger$observePassage(CallbackInfo ci) {
        if (spmscavenger$episodeDoor == null) {
            return;
        }
        float currentDx = (float) (spmscavenger$episodeDoor.getX() + 0.5D - this.mob.getX());
        float currentDz = (float) (spmscavenger$episodeDoor.getZ() + 0.5D - this.mob.getZ());
        if (spmscavenger$startDoorDx * currentDx + spmscavenger$startDoorDz * currentDz < 0.0F) {
            spmscavenger$crossed = true;
        }
    }

    /**
     * Close-behind means behind: timeout before crossing leaves the door open for navigation or a
     * fresh path. Record the encounter before the host optionally schedules its one close action.
     */
    @Inject(method = "stop", at = @At("HEAD"), cancellable = true, require = 0)
    private void spmscavenger$finishPassageEpisode(CallbackInfo ci) {
        if (spmscavenger$episodeDoor != null) {
            spmscavenger$lastEpisodeDoor = spmscavenger$episodeDoor;
            spmscavenger$lastEpisodePath = this.mob.getNavigation().getPath();
            spmscavenger$lastDoorOpen = this.isOpen();
            spmscavenger$lastEpisodeCrossed = spmscavenger$crossed;
        }
        if (!DoorPassagePolicy.closeAfterEpisode(spmscavenger$crossed)) {
            ci.cancel();
        }
        spmscavenger$episodeDoor = null;
    }

    @Unique
    private static boolean spmscavenger$isBusy(Object goal) {
        if (!spmscavenger$resolved) {
            spmscavenger$resolved = true;
            try {
                spmscavenger$playerMobField = goal.getClass().getDeclaredField("playerMob");
                spmscavenger$playerMobField.setAccessible(true);
                Object host = spmscavenger$playerMobField.get(goal);
                spmscavenger$isOperatingDoor = host.getClass().getMethod("isOperatingDoor");
                spmscavenger$isRecovering = host.getClass().getMethod("isRecovering");
            } catch (ReflectiveOperationException | RuntimeException e) {
                spmscavenger$isOperatingDoor = null;
                spmscavenger$isRecovering = null;
                spmscavenger$playerMobField = null;
                spmscavenger$warnUnavailable(e);
            }
        }
        if (spmscavenger$isOperatingDoor == null
                || spmscavenger$isRecovering == null
                || spmscavenger$playerMobField == null) {
            return false;
        }
        try {
            Object host = spmscavenger$playerMobField.get(goal);
            return Boolean.TRUE.equals(spmscavenger$isOperatingDoor.invoke(host))
                    || Boolean.TRUE.equals(spmscavenger$isRecovering.invoke(host));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            spmscavenger$warnUnavailable(e);
            return false;
        }
    }

    @Unique
    private static boolean spmscavenger$isOperating(Object goal) {
        if (!spmscavenger$resolved) {
            spmscavenger$isBusy(goal);
        }
        if (spmscavenger$isOperatingDoor == null || spmscavenger$playerMobField == null) {
            return false;
        }
        try {
            Object host = spmscavenger$playerMobField.get(goal);
            return Boolean.TRUE.equals(spmscavenger$isOperatingDoor.invoke(host));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            spmscavenger$warnUnavailable(e);
            return false;
        }
    }

    @Unique
    private static void spmscavenger$warnUnavailable(Throwable cause) {
        if (spmscavenger$warned) {
            return;
        }
        spmscavenger$warned = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] SPM door-operation arbitration shape changed; busy-request guard "
                        + "disabled and stock SPM behavior retained. This addon may need an update.",
                cause);
    }
}
