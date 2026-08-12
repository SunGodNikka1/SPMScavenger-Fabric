package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.goal.ShelterNightAuthority;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Optional SPM v0.86 scheduler repair for an already-arrived shelter hold.
 *
 * <p>SPM's entity owns and ticks the actual face/swing/open-or-close operation. Its
 * {@code DoorOperationGoal} only claims MOVE+LOOK while that operation is active. Once shelter has
 * settled, taking MOVE away from {@code SeekShelterGoal} creates a needless hold -> Idle -> leave
 * -> re-seek loop. Suppressing only that scheduler wrapper preserves the physical door action and
 * lets the arrived shelter commitment continue owning MOVE until dawn.
 */
@Pseudo
@Mixin(targets = "games.brennan.playermob.entity.goal.DoorOperationGoal")
public abstract class DoorOperationShelterHoldMixin {

    @Unique
    private static Field spmscavenger$mobField;

    @Unique
    private static boolean spmscavenger$resolved;

    @Unique
    private static boolean spmscavenger$warned;

    /**
     * Applies to both admission and continuation so a helper already running at the handoff cannot
     * retain MOVE after the shelter commitment reaches ARRIVED. {@code require = 0} keeps SPM
     * optional and preserves stock behavior if the host goal changes shape.
     */
    @Inject(
            method = {"canUse", "canContinueToUse"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void spmscavenger$preserveArrivedShelterAuthority(
            CallbackInfoReturnable<Boolean> cir) {
        Mob mob = spmscavenger$mob(this);
        if (mob != null && ShelterNightAuthority.isSettled(mob.getUUID())) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static Mob spmscavenger$mob(Object goal) {
        if (!spmscavenger$resolved) {
            spmscavenger$resolved = true;
            try {
                spmscavenger$mobField = goal.getClass().getDeclaredField("mob");
                spmscavenger$mobField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                spmscavenger$mobField = null;
                spmscavenger$warnUnavailable(e);
            }
        }
        if (spmscavenger$mobField == null) {
            return null;
        }
        try {
            Object value = spmscavenger$mobField.get(goal);
            return value instanceof Mob mob ? mob : null;
        } catch (IllegalAccessException | RuntimeException e) {
            spmscavenger$warnUnavailable(e);
            return null;
        }
    }

    @Unique
    private static void spmscavenger$warnUnavailable(Throwable cause) {
        if (spmscavenger$warned) {
            return;
        }
        spmscavenger$warned = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] SPM DoorOperationGoal shape changed; arrived-shelter hold guard "
                        + "disabled and stock SPM scheduling retained. This addon may need an update.",
                cause);
    }
}
