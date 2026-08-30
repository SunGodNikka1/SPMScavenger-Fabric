package com.noobk.spmscavenger.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * Validation-owned, synchronous, non-damaging V4 fixture cleanup.
 *
 * <p>Legacy timer removal uses the same direct {@link TimerQueue} API as Minecraft's schedule
 * command implementation. Entity teardown uses {@link Entity#discard()} and never gameplay
 * damage, health mutation, command functions, or delayed work.
 */
final class V4FixtureCleanup {

    static final String FIXTURE_TAG = "spm_v4.fixture";
    private static final ResourceLocation LEGACY_CLEANUP_FUNCTION =
            ResourceLocation.fromNamespaceAndPath("spm_v4", "cleanup");

    private V4FixtureCleanup() {
    }

    static void prepareForStartup(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        diagnostics.cleanupAttempted = true;
        long startedAtTick = level.getGameTime();
        purgeLegacySchedule(level.getServer(), diagnostics);

        AABB bounds = cleanupBounds(origin);
        List<Entity> candidates = taggedEntities(level, bounds);
        diagnostics.staleFixtureCandidates = candidates.size();
        discard(candidates, diagnostics);
        diagnostics.staleFixtureEntitiesRemaining = taggedEntities(level, bounds).size();

        complete(level, startedAtTick, diagnostics);
    }

    static void discardOwned(
            ServerLevel level, Collection<UUID> ownedIds, Diagnostics diagnostics) {
        diagnostics.cleanupAttempted = true;
        long startedAtTick = level.getGameTime();
        purgeLegacySchedule(level.getServer(), diagnostics);

        Set<UUID> uniqueIds = new LinkedHashSet<>(ownedIds);
        List<Entity> candidates = new ArrayList<>();
        for (UUID id : uniqueIds) {
            if (id == null) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity != null && entity.getTags().contains(FIXTURE_TAG)) {
                candidates.add(entity);
            }
        }
        diagnostics.staleFixtureCandidates = candidates.size();
        discard(candidates, diagnostics);

        int remaining = 0;
        for (UUID id : uniqueIds) {
            if (id == null) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity != null && entity.getTags().contains(FIXTURE_TAG)) {
                remaining++;
            }
        }
        diagnostics.staleFixtureEntitiesRemaining = remaining;
        complete(level, startedAtTick, diagnostics);
    }

    static AABB cleanupBounds(BlockPos origin) {
        return new AABB(
                origin.getX() - 32.0D, origin.getY() - 8.0D, origin.getZ() - 32.0D,
                origin.getX() + 213.0D, origin.getY() + 9.0D, origin.getZ() + 33.0D);
    }

    private static void purgeLegacySchedule(
            MinecraftServer server, Diagnostics diagnostics) {
        TimerQueue<MinecraftServer> scheduledEvents =
                server.getWorldData().overworldData().getScheduledEvents();
        String legacyId = LEGACY_CLEANUP_FUNCTION.toString();
        diagnostics.legacyCleanupSchedulePresentBefore =
                scheduledEvents.getEventsIds().contains(legacyId);
        diagnostics.legacyCleanupScheduleEntriesRemoved = scheduledEvents.remove(legacyId);
        diagnostics.legacyCleanupSchedulePresentAfter =
                scheduledEvents.getEventsIds().contains(legacyId);
        diagnostics.legacyCleanupScheduleCleared =
                !diagnostics.legacyCleanupSchedulePresentAfter
                        && (!diagnostics.legacyCleanupSchedulePresentBefore
                                || diagnostics.legacyCleanupScheduleEntriesRemoved > 0);
        if (!diagnostics.legacyCleanupScheduleCleared) {
            throw diagnostics.fail("legacy_schedule",
                    "legacy cleanup timer remains or could not be proven removed");
        }
    }

    private static List<Entity> taggedEntities(ServerLevel level, AABB bounds) {
        return List.copyOf(level.getEntitiesOfClass(Entity.class, bounds,
                entity -> entity.getTags().contains(FIXTURE_TAG)));
    }

    private static void discard(List<Entity> candidates, Diagnostics diagnostics) {
        for (Entity entity : candidates) {
            entity.discard();
            if (entity.isRemoved()) {
                diagnostics.staleFixtureEntitiesDiscarded++;
            }
        }
    }

    private static void complete(
            ServerLevel level, long startedAtTick, Diagnostics diagnostics) {
        diagnostics.cleanupCompletedTick = level.getGameTime();
        diagnostics.cleanupCompletedSynchronously =
                diagnostics.cleanupCompletedTick == startedAtTick
                        && diagnostics.staleFixtureEntitiesRemaining == 0
                        && !diagnostics.legacyCleanupSchedulePresentAfter
                        && diagnostics.legacyCleanupScheduleCleared;
        if (!diagnostics.cleanupCompletedSynchronously) {
            throw diagnostics.fail("completion",
                    "cleanup did not complete synchronously and empty");
        }
        diagnostics.failureStage = "NONE";
        diagnostics.failureDetail = "NONE";
    }

    static final class Diagnostics {
        boolean cleanupAttempted;
        int staleFixtureCandidates;
        int staleFixtureEntitiesDiscarded;
        int staleFixtureEntitiesRemaining;
        boolean cleanupCompletedSynchronously;
        long cleanupCompletedTick = -1L;
        boolean legacyCleanupSchedulePresentBefore;
        boolean legacyCleanupScheduleCleared;
        boolean legacyCleanupSchedulePresentAfter;
        int legacyCleanupScheduleEntriesRemoved;
        String failureStage = "NOT_RUN";
        String failureDetail = "NOT_RUN";

        boolean ready() {
            return cleanupAttempted
                    && staleFixtureEntitiesRemaining == 0
                    && cleanupCompletedSynchronously
                    && legacyCleanupScheduleCleared
                    && !legacyCleanupSchedulePresentAfter;
        }

        IllegalStateException fail(String stage, String detail) {
            failureStage = stage;
            failureDetail = detail;
            return new IllegalStateException("fixture cleanup " + stage + ": " + detail);
        }

        List<String> lines() {
            return List.of(
                    "cleanupOwner=VALIDATION_JAVA cleanupCommandFunctionInvoked=NO",
                    "cleanupAttempted=" + yesNo(cleanupAttempted)
                            + " staleFixtureCandidates=" + staleFixtureCandidates
                            + " staleFixtureEntitiesDiscarded="
                            + staleFixtureEntitiesDiscarded
                            + " staleFixtureEntitiesRemaining="
                            + staleFixtureEntitiesRemaining,
                    "cleanupCompletedSynchronously=" + yesNo(cleanupCompletedSynchronously)
                            + " cleanupCompletedTick=" + cleanupCompletedTick,
                    "legacyCleanupSchedulePresentBefore="
                            + yesNo(legacyCleanupSchedulePresentBefore)
                            + " legacyCleanupScheduleCleared="
                            + yesNo(legacyCleanupScheduleCleared)
                            + " legacyCleanupSchedulePresentAfter="
                            + yesNo(legacyCleanupSchedulePresentAfter)
                            + " entriesRemoved=" + legacyCleanupScheduleEntriesRemoved,
                    "cleanupGate=" + (ready() ? "PASS" : "FAIL")
                            + " failureStage=" + failureStage
                            + " failureDetail=" + failureDetail);
        }

        private static String yesNo(boolean value) {
            return value ? "YES" : "NO";
        }
    }
}
