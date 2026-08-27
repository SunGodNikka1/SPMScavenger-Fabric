package com.noobk.spmscavenger.compat;

import com.noobk.spmscavenger.PlayerMobs;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.lang.reflect.Constructor;

/**
 * Retunes SPM {@code WeaponAwareAttackGoal} chase speed without a compile dependency.
 *
 * <p>SPM registers the goal at priority 2 with a hardcoded {@code 1.0} pathfinder multiplier. This
 * addon replaces that goal instance on {@code ENTITY_LOAD} so melee/ranged <em>approach</em> runs at
 * {@link com.noobk.spmscavenger.ScavengerConfig#combatChaseSpeed}. In-range bow strafe and shot
 * cadence remain SPM-owned.
 */
public final class SpmCombatChaseSpeed {

    static final String WEAPON_AWARE_ATTACK_GOAL =
            "games.brennan.playermob.entity.goal.WeaponAwareAttackGoal";

    /** Revalidated in SPM 0.96.0 {@code PlayerMobEntity#registerGoals}. */
    private static final float DEFAULT_RANGED_ATTACK_RANGE = 8.0f;

    private static boolean warnedMissing;

    private SpmCombatChaseSpeed() {}

    public static double clampSpeed(double speed) {
        return Mth.clamp(speed, 1.0, 1.5);
    }

    public static void apply(Mob mob, GoalSelector selector) {
        if (!PlayerMobs.available() || !PlayerMobs.isPlayerMob(mob)) {
            return;
        }
        double speed = clampSpeed(ScavengerConfig.get().combatChaseSpeed);
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (!WEAPON_AWARE_ATTACK_GOAL.equals(goal.getClass().getName())) {
                continue;
            }
            int priority = wrapped.getPriority();
            selector.removeGoal(goal);
            Goal replacement = createReplacement(mob, speed);
            if (replacement == null) {
                selector.addGoal(priority, goal);
                return;
            }
            selector.addGoal(priority, replacement);
            return;
        }
        warnMissingOnce();
    }

    private static Goal createReplacement(Mob mob, double speed) {
        try {
            Class<?> goalType = Class.forName(WEAPON_AWARE_ATTACK_GOAL);
            Class<?> mobType = PlayerMobs.playerMobClass();
            if (mobType == null || !mobType.isInstance(mob)) {
                return null;
            }
            Constructor<?> ctor = goalType.getConstructor(mobType, double.class, float.class);
            return (Goal) ctor.newInstance(mob, speed, DEFAULT_RANGED_ATTACK_RANGE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] could not retune SPM fighting chase speed; using stock 1.0. "
                            + "This mod likely needs an update.",
                    e);
            return null;
        }
    }

    private static void warnMissingOnce() {
        if (warnedMissing) {
            return;
        }
        warnedMissing = true;
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] WeaponAwareAttackGoal not found on PlayerMob; fighting chase speed "
                        + "left at SPM default. This mod likely needs an update.");
    }
}
