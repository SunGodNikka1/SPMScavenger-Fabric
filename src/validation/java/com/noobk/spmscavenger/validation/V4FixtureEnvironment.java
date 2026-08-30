package com.noobk.spmscavenger.validation;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

/** Validation-owned environmental isolation for the bounded V4-G campaign arena. */
final class V4FixtureEnvironment {

    private V4FixtureEnvironment() {
    }

    static void prepareBeforeEntityCreation(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        if (!diagnostics.doMobSpawningCaptured) {
            diagnostics.originalDoMobSpawning = level.getGameRules().getBoolean(
                    GameRules.RULE_DOMOBSPAWNING);
            diagnostics.doMobSpawningCaptured = true;
        }
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                .set(false, level.getServer());
        diagnostics.doMobSpawningDisabled = !level.getGameRules().getBoolean(
                GameRules.RULE_DOMOBSPAWNING);
        if (!diagnostics.doMobSpawningDisabled) {
            throw diagnostics.fail("mob_spawning", "doMobSpawning remained enabled");
        }

        AABB arena = V4FixtureCleanup.cleanupBounds(origin);
        List<Entity> hostiles = foreignHostiles(level, arena);
        diagnostics.bootstrapForeignHostilesFound = hostiles.size();
        for (Entity hostile : hostiles) {
            hostile.discard();
            if (hostile.isRemoved()) {
                diagnostics.bootstrapForeignHostilesDiscarded++;
            }
        }
        diagnostics.bootstrapForeignHostilesRemaining = foreignHostiles(level, arena).size();
        if (diagnostics.bootstrapForeignHostilesRemaining != 0) {
            throw diagnostics.fail("foreign_hostiles",
                    diagnostics.bootstrapForeignHostilesRemaining
                            + " unrelated hostile entity/entities remain");
        }
        diagnostics.preflightComplete = true;
        diagnostics.failureStage = "NONE";
        diagnostics.failureDetail = "NONE";
    }

    static boolean restore(ServerLevel level, Diagnostics diagnostics) {
        if (!diagnostics.doMobSpawningCaptured) {
            diagnostics.restoreNotRequired = true;
            return true;
        }
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                .set(diagnostics.originalDoMobSpawning, level.getServer());
        diagnostics.doMobSpawningRestored =
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                        == diagnostics.originalDoMobSpawning;
        diagnostics.restoreTick = level.getGameTime();
        if (!diagnostics.doMobSpawningRestored) {
            diagnostics.failureStage = "gamerule_restore";
            diagnostics.failureDetail = "doMobSpawning did not return to captured value";
        }
        return diagnostics.doMobSpawningRestored;
    }

    private static List<Entity> foreignHostiles(ServerLevel level, AABB arena) {
        return List.copyOf(level.getEntitiesOfClass(Entity.class, arena,
                entity -> entity instanceof Enemy
                        && !entity.getTags().contains(V4FixtureCleanup.FIXTURE_TAG)));
    }

    static final class Diagnostics {
        boolean doMobSpawningCaptured;
        boolean originalDoMobSpawning;
        boolean doMobSpawningDisabled;
        boolean doMobSpawningRestored;
        boolean restoreNotRequired;
        long restoreTick = -1L;
        int bootstrapForeignHostilesFound;
        int bootstrapForeignHostilesDiscarded;
        int bootstrapForeignHostilesRemaining = -1;
        boolean preflightComplete;
        String failureStage = "NOT_RUN";
        String failureDetail = "NOT_RUN";

        boolean readyForEntityCreation() {
            return doMobSpawningCaptured
                    && doMobSpawningDisabled
                    && preflightComplete
                    && bootstrapForeignHostilesRemaining == 0;
        }

        IllegalStateException fail(String stage, String detail) {
            failureStage = stage;
            failureDetail = detail;
            return new IllegalStateException("fixture environment " + stage + ": " + detail);
        }

        List<String> lines() {
            return List.of(
                    "doMobSpawningCaptured=" + yesNo(doMobSpawningCaptured)
                            + " original=" + measured(doMobSpawningCaptured,
                                    originalDoMobSpawning)
                            + " disabledForCampaign=" + yesNo(doMobSpawningDisabled),
                    "doMobSpawningRestored="
                            + (restoreNotRequired ? "NOT_REQUIRED"
                                    : yesNo(doMobSpawningRestored))
                            + " restoreTick=" + restoreTick,
                    "bootstrapForeignHostilesFound=" + bootstrapForeignHostilesFound
                            + " bootstrapForeignHostilesDiscarded="
                            + bootstrapForeignHostilesDiscarded
                            + " bootstrapForeignHostilesRemaining="
                            + bootstrapForeignHostilesRemaining,
                    "environmentIsolationGate="
                            + (readyForEntityCreation() ? "PASS" : "FAIL")
                            + " failureStage=" + failureStage
                            + " failureDetail=" + failureDetail);
        }

        private static String measured(boolean measured, boolean value) {
            return measured ? Boolean.toString(value) : "NOT_MEASURED";
        }

        private static String yesNo(boolean value) {
            return value ? "YES" : "NO";
        }
    }
}
