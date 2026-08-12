package com.noobk.spmscavenger.mixin;

import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared optional-host field resolver; compatibility Mixins contain no policy of their own. */
final class OptionalGoalMobResolver {

    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING = new ConcurrentHashMap<>();

    private OptionalGoalMobResolver() {
    }

    static Mob resolve(Object goal, String integration) {
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
