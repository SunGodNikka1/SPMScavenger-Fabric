package com.noobk.spmscavenger.compat;

import com.noobk.spmscavenger.SpmScavenger;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Narrow host boundary for pinned SPM {@code RaidContainersGoal#targetPos} (Gate 0-B).
 *
 * <p>Exposes only {@link #resolveTarget} and {@link #clearTarget} — no other host mutation.
 */
public final class OptionalRaidContainerTargetResolver {

    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING = new ConcurrentHashMap<>();

    private OptionalRaidContainerTargetResolver() {
    }

    public static Optional<BlockPos> resolveTarget(Object goal) {
        Field field = field(goal);
        if (field == null) {
            return Optional.empty();
        }
        try {
            Object value = field.get(goal);
            if (value instanceof BlockPos pos) {
                return Optional.of(pos.immutable());
            }
            return Optional.empty();
        } catch (IllegalAccessException | RuntimeException e) {
            markMissing(goal.getClass(), e);
            return Optional.empty();
        }
    }

    public static void clearTarget(Object goal) {
        Field field = field(goal);
        if (field == null) {
            return;
        }
        try {
            field.set(goal, null);
        } catch (IllegalAccessException | RuntimeException e) {
            markMissing(goal.getClass(), e);
        }
    }

    private static Field field(Object goal) {
        Class<?> type = goal.getClass();
        if (MISSING.containsKey(type)) {
            return null;
        }
        Field cached = FIELDS.get(type);
        if (cached != null) {
            return cached;
        }
        try {
            Field field = type.getDeclaredField("targetPos");
            field.setAccessible(true);
            FIELDS.put(type, field);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            markMissing(type, e);
            return null;
        }
    }

    private static void markMissing(Class<?> type, Exception e) {
        MISSING.put(type, Boolean.TRUE);
        FIELDS.remove(type);
        SpmScavenger.LOGGER.warn(
                "[spmscavenger] SPM raid storage goal shape changed; ally storage guard disabled "
                        + "for {} and stock scheduling retained.",
                type.getName(), e);
    }
}
