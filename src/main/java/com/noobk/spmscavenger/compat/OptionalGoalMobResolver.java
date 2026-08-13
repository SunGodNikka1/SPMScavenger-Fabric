package com.noobk.spmscavenger.compat;

import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared optional-host field resolver; compatibility Mixins contain no policy of their own.
 *
 * <p>Lives in {@code compat}, deliberately <b>not</b> in {@code mixin}. Mixin owns every class under
 * a package declared in {@code spmscavenger.mixins.json} and refuses to load any of them as an
 * ordinary class: {@code IllegalClassLoadError: ... is in a defined mixin package ... and cannot be
 * referenced directly}. A plain helper there compiles, packages and loads fine - then throws the
 * first time an injected handler actually calls it. It stayed invisible only because the SPM goal
 * mixins were silently inert; the moment they began applying, this crashed the server on the first
 * greet tick.
 *
 * <p>(Interface mixins such as {@code MobGoalSelectorAccessor} are exempt - they are applied to the
 * target and are meant to be referenced from outside.)
 */
public final class OptionalGoalMobResolver {

    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING = new ConcurrentHashMap<>();

    private OptionalGoalMobResolver() {
    }

    public static Mob resolve(Object goal, String integration) {
        Class<?> type = goal.getClass();
        if (MISSING.containsKey(type)) {
            return null;
        }
        Field field = FIELDS.get(type);
        if (field == null) {
            try {
                field = type.getDeclaredField("mob");
                field.setAccessible(true);
                FIELDS.put(type, field);
            } catch (ReflectiveOperationException | RuntimeException e) {
                MISSING.put(type, Boolean.TRUE);
                SpmScavenger.LOGGER.warn(
                        "[spmscavenger] SPM {} goal shape changed; shelter compatibility guard "
                                + "disabled for {} and stock scheduling retained.",
                        integration, type.getName(), e);
                return null;
            }
        }
        try {
            Object value = field.get(goal);
            return value instanceof Mob mob ? mob : null;
        } catch (IllegalAccessException | RuntimeException e) {
            FIELDS.remove(type);
            MISSING.put(type, Boolean.TRUE);
            SpmScavenger.LOGGER.warn(
                    "[spmscavenger] SPM {} mob field became unreadable; shelter compatibility "
                            + "guard disabled and stock scheduling retained.", integration, e);
            return null;
        }
    }
}
