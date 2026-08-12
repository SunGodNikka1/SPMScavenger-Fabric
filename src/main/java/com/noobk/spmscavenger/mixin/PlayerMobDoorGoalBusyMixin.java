package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.SpmScavenger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional SPM v0.86 door arbitration repair; never owns or operates a door itself. */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.PlayerMobDoorGoal")
public abstract class PlayerMobDoorGoalBusyMixin {

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
