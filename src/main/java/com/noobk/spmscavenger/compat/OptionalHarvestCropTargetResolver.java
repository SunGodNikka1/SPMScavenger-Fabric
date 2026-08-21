package com.noobk.spmscavenger.compat;

import com.noobk.spmscavenger.SpmScavenger;
import com.noobk.spmscavenger.village.crop.HarvestCropGuardCompatibility;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective access to pinned SPM {@code HarvestCropsGoal#targetPos} (task-55 Gate 0-H).
 */
public final class OptionalHarvestCropTargetResolver {

    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING = new ConcurrentHashMap<>();

    private OptionalHarvestCropTargetResolver() {
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
            HarvestCropGuardCompatibility.markHostShapeSupported();
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
                "[spmscavenger] SPM harvest crop goal shape changed; managed-domain veto cannot "
                        + "read targetPos for {} — fail-open until restored.",
                type.getName(), e);
    }
}
