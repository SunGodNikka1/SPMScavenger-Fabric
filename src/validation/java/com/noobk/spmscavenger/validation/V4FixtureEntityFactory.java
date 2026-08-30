package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.PlayerMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Validation-only checked creation boundary for every entity required by V4-G.
 *
 * <p>The PlayerMob path deliberately mirrors the pinned SPM v0.96
 * {@code PlayerMobSummon}: resolve its registered type, create, position, finalize with the
 * command spawn reason, and add to the level. This class does not set AI state or call any
 * Scavenger authority surface.
 */
final class V4FixtureEntityFactory {

    static final ResourceLocation PLAYER_MOB_ID =
            ResourceLocation.fromNamespaceAndPath("playermob", "player_mob");
    private static final String FIXTURE_TAG = "spm_v4.fixture";
    private static final String SUBJECT_TAG = "spm_v4.subject";
    private static final String TRADER_TAG = "spm_v4.trader";
    private static final String HELPER_TAG = "spm_v4.helper";

    private V4FixtureEntityFactory() {
    }

    static VerifiedFixture createAndVerify(
            ServerLevel level, BlockPos origin, Diagnostics diagnostics) {
        diagnostics.playerMobRegistryId = PLAYER_MOB_ID.toString();
        diagnostics.difficulty = level.getDifficulty().getKey();
        diagnostics.playerMobsCompatibilityAvailable = PlayerMobs.available();
        if (!diagnostics.playerMobsCompatibilityAvailable) {
            throw diagnostics.fail("host_class",
                    "SPM PlayerMobEntity compatibility class is unavailable");
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            throw diagnostics.fail("environment",
                    "difficulty is PEACEFUL; SPM PlayerMobs are monster-category entities and "
                            + "the pinned host removes them in Peaceful");
        }

        Optional<EntityType<?>> playerMobType =
                BuiltInRegistries.ENTITY_TYPE.getOptional(PLAYER_MOB_ID);
        diagnostics.entityTypePresent = playerMobType.isPresent();
        if (playerMobType.isEmpty()) {
            throw diagnostics.fail("registry",
                    "entity type is not registered: " + PLAYER_MOB_ID);
        }

        BlockPos subjectPos = origin.offset(2, 0, 0);
        diagnostics.subjectChunkReady = level.hasChunkAt(subjectPos);
        if (!diagnostics.subjectChunkReady) {
            throw diagnostics.fail("subject_chunk", "subject chunk is not ready at " + subjectPos);
        }

        diagnostics.spawnAttempted = true;
        Entity created = playerMobType.get().create(level);
        if (created == null) {
            throw diagnostics.fail("subject_construct", "registered EntityType.create returned null");
        }
        diagnostics.spawnedUUID = created.getUUID();
        diagnostics.spawnedRuntimeClass = created.getClass().getName();
        if (!(created instanceof Mob subject)) {
            created.discard();
            throw diagnostics.fail("subject_construct",
                    "registered type created non-Mob runtime class "
                            + diagnostics.spawnedRuntimeClass);
        }
        spawnMob(level, subject, subjectPos, List.of(FIXTURE_TAG, SUBJECT_TAG), diagnostics,
                "subject");
        diagnostics.spawnSucceeded = true;
        diagnostics.levelEntityResolvable = level.getEntity(subject.getUUID()) == subject;
        diagnostics.expectedTagsPresent = hasTags(subject, FIXTURE_TAG, SUBJECT_TAG);
        diagnostics.playerMobsIsPlayerMob = PlayerMobs.isPlayerMob(subject);
        if (!diagnostics.levelEntityResolvable) {
            throw diagnostics.fail("subject_attach",
                    "level.getEntity(UUID) did not resolve the exact spawned subject");
        }
        if (!diagnostics.expectedTagsPresent) {
            throw diagnostics.fail("subject_tags", "expected fixture/subject tags are absent");
        }
        if (!diagnostics.playerMobsIsPlayerMob) {
            throw diagnostics.fail("subject_identity",
                    "spawned entity is not recognized by PlayerMobs.isPlayerMob");
        }

        Villager trader = createVillager(level, origin.offset(-1, 0, 0), TRADER_TAG,
                diagnostics, "trader");
        diagnostics.traderCreated = true;
        diagnostics.traderUUID = trader.getUUID();
        diagnostics.traderRuntimeClass = trader.getClass().getName();

        Villager helper = createVillager(level, origin.offset(-7, 0, 1), HELPER_TAG,
                diagnostics, "helper");
        diagnostics.helperCreated = true;
        diagnostics.helperUUID = helper.getUUID();
        diagnostics.helperRuntimeClass = helper.getClass().getName();

        if (!diagnostics.ready()) {
            throw diagnostics.fail("attachment_gate",
                    "one or more required fixture entities failed verified attachment");
        }
        diagnostics.failureStage = "NONE";
        diagnostics.failureDetail = "NONE";
        return new VerifiedFixture(subject, trader, helper);
    }

    private static Villager createVillager(
            ServerLevel level, BlockPos pos, String roleTag, Diagnostics diagnostics,
            String role) {
        if (!level.hasChunkAt(pos)) {
            throw diagnostics.fail(role + "_chunk", role + " chunk is not ready at " + pos);
        }
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            throw diagnostics.fail(role + "_construct", "EntityType.VILLAGER.create returned null");
        }
        spawnMob(level, villager, pos, List.of(FIXTURE_TAG, roleTag), diagnostics, role);
        if (level.getEntity(villager.getUUID()) != villager) {
            throw diagnostics.fail(role + "_attach",
                    "level.getEntity(UUID) did not resolve the exact spawned " + role);
        }
        if (!hasTags(villager, FIXTURE_TAG, roleTag)) {
            throw diagnostics.fail(role + "_tags", "expected fixture/" + role + " tags are absent");
        }
        return villager;
    }

    private static void spawnMob(
            ServerLevel level, Mob mob, BlockPos pos, List<String> tags,
            Diagnostics diagnostics, String role) {
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        tags.forEach(mob::addTag);
        mob.setPersistenceRequired();
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        } catch (RuntimeException failure) {
            mob.discard();
            throw diagnostics.fail(role + "_finalize",
                    failure.getClass().getSimpleName() + conciseMessage(failure));
        }
        if (!level.addFreshEntity(mob)) {
            mob.discard();
            throw diagnostics.fail(role + "_add", "ServerLevel.addFreshEntity returned false");
        }
    }

    private static boolean hasTags(Entity entity, String... tags) {
        for (String tag : tags) {
            if (!entity.getTags().contains(tag)) {
                return false;
            }
        }
        return true;
    }

    private static String conciseMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "" : ": " + message;
    }

    record VerifiedFixture(Mob subject, Villager trader, Villager helper) {
    }

    static final class Diagnostics {
        String playerMobRegistryId = "UNREAD";
        boolean entityTypePresent;
        boolean spawnAttempted;
        boolean spawnSucceeded;
        UUID spawnedUUID;
        String spawnedRuntimeClass = "UNAVAILABLE";
        boolean expectedTagsPresent;
        boolean levelEntityResolvable;
        boolean playerMobsCompatibilityAvailable;
        boolean playerMobsIsPlayerMob;
        boolean traderCreated;
        UUID traderUUID;
        String traderRuntimeClass = "UNAVAILABLE";
        boolean helperCreated;
        UUID helperUUID;
        String helperRuntimeClass = "UNAVAILABLE";
        boolean subjectChunkReady;
        String difficulty = "UNREAD";
        String failureStage = "NOT_RUN";
        String failureDetail = "NOT_RUN";

        boolean ready() {
            return entityTypePresent && spawnAttempted && spawnSucceeded
                    && spawnedUUID != null && expectedTagsPresent && levelEntityResolvable
                    && playerMobsCompatibilityAvailable && playerMobsIsPlayerMob
                    && traderCreated && traderUUID != null && helperCreated && helperUUID != null;
        }

        IllegalStateException fail(String stage, String detail) {
            failureStage = stage;
            failureDetail = detail;
            return new IllegalStateException("fixture entity preflight " + stage + ": " + detail);
        }

        List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add("playerMobRegistryId=" + playerMobRegistryId);
            lines.add("entityTypePresent=" + yesNo(entityTypePresent)
                    + " PlayerMobs.available=" + yesNo(playerMobsCompatibilityAvailable)
                    + " difficulty=" + difficulty + " subjectChunkReady="
                    + yesNo(subjectChunkReady));
            lines.add("spawnAttempted=" + yesNo(spawnAttempted)
                    + " spawnSucceeded=" + yesNo(spawnSucceeded)
                    + " spawnedUUID=" + printable(spawnedUUID)
                    + " spawnedRuntimeClass=" + spawnedRuntimeClass);
            lines.add("expectedTagsPresent=" + yesNo(expectedTagsPresent)
                    + " levelEntityResolvable=" + yesNo(levelEntityResolvable)
                    + " PlayerMobs.isPlayerMob=" + yesNo(playerMobsIsPlayerMob));
            lines.add("traderCreated=" + yesNo(traderCreated)
                    + " traderUUID=" + printable(traderUUID)
                    + " traderRuntimeClass=" + traderRuntimeClass);
            lines.add("helperCreated=" + yesNo(helperCreated)
                    + " helperUUID=" + printable(helperUUID)
                    + " helperRuntimeClass=" + helperRuntimeClass);
            lines.add("fixtureAttachmentGate=" + (ready() ? "PASS" : "FAIL")
                    + " failureStage=" + failureStage + " failureDetail=" + failureDetail);
            return List.copyOf(lines);
        }

        private static String yesNo(boolean value) {
            return value ? "YES" : "NO";
        }

        private static String printable(Object value) {
            return value == null ? "UNAVAILABLE" : value.toString();
        }
    }
}
