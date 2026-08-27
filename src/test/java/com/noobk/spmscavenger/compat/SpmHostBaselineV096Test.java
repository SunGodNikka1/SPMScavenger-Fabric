package com.noobk.spmscavenger.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task 66 host-sync gate: the exact optional SPM v0.96 compatibility oracle is pinned. */
class SpmHostBaselineV096Test {

    private static final Set<String> REQUIRED_CLASSES = Set.of(
            "games/brennan/playermob/entity/PlayerMobEntity.class",
            "games/brennan/playermob/entity/FeelingLedger.class",
            "games/brennan/playermob/entity/ObjectiveReadout.class",
            "games/brennan/playermob/client/PlayerMobRenderer.class",
            "games/brennan/playermob/entity/goal/FriendlyGreetGoal.class",
            "games/brennan/playermob/entity/goal/FriendlyGreetGoal$Phase.class",
            "games/brennan/playermob/entity/goal/FollowLovedOneGoal.class",
            "games/brennan/playermob/entity/goal/SeekAmmoGoal.class",
            "games/brennan/playermob/entity/goal/HarvestCropsGoal.class",
            "games/brennan/playermob/entity/goal/RaidContainersGoal.class",
            "games/brennan/playermob/entity/goal/RaidArmorStandsGoal.class",
            "games/brennan/playermob/entity/goal/CollectFloorItemsGoal.class",
            "games/brennan/playermob/entity/goal/AdvanceCarriageGoal.class",
            "games/brennan/playermob/entity/goal/CrossGroupGapGoal.class",
            "games/brennan/playermob/entity/goal/PlayerMobDoorGoal.class",
            "games/brennan/playermob/entity/goal/DoorOperationGoal.class",
            "games/brennan/playermob/entity/goal/DouseFireInPathGoal.class",
            "games/brennan/playermob/entity/goal/FlintAndSteelIgniteGoal.class",
            "games/brennan/playermob/entity/goal/WeaponAwareAttackGoal.class",
            "games/brennan/playermob/entity/goal/TntCombatGoal.class",
            "games/brennan/playermob/entity/goal/EndCrystalCombatGoal.class",
            "games/brennan/playermob/mixin/OwnableEntityOwnerMixin.class",
            "games/brennan/playermob/mixin/RaiderTargetsPlayerMobMixin.class");

    @Test
    void mustHappen_exactV096ArtifactAndMetadataAreTheCompatibilityOracle() throws Exception {
        Path jar = referenceJar();
        byte[] bytes = Files.readAllBytes(jar);
        String actual = HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals(System.getProperty("spm.reference.sha256"), actual);
        assertEquals("0.96.0", System.getProperty("spm.reference.version"));

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            JsonObject metadata;
            try (InputStream input = requiredEntry(zip, "fabric.mod.json")) {
                metadata = JsonParser.parseReader(
                        new java.io.InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            }
            assertEquals("playermob", metadata.get("id").getAsString());
            assertEquals("0.96.0", metadata.get("version").getAsString());
            for (String required : REQUIRED_CLASSES) {
                assertNotNull(zip.getEntry(required), "missing v0.96 seam: " + required);
            }
        }
    }

    @Test
    void mustHappen_allDirectlyReferencedPlayerMobSymbolsRemainPresent() throws Exception {
        try (ZipFile zip = new ZipFile(referenceJar().toFile())) {
            String playerMob = constants(zip,
                    "games/brennan/playermob/entity/PlayerMobEntity.class");
            for (String symbol : Set.of(
                    "fightFlight", "friendliness", "feelingToward", "getStayAnchor",
                    "attackOrder", "nearestWhereReaction")) {
                assertTrue(playerMob.contains(symbol), "missing PlayerMob seam: " + symbol);
            }
            assertTrue(playerMob.contains(
                            "(Lgames/brennan/playermob/entity/Reaction;D)Lnet/minecraft/class_1309;"),
                    "FriendlyGreet redirect call descriptor changed");

            for (String goal : Set.of("HarvestCropsGoal", "RaidContainersGoal")) {
                String goalConstants = constants(zip,
                        "games/brennan/playermob/entity/goal/" + goal + ".class");
                assertTrue(goalConstants.contains("mob"), goal + " lost its host-mob field seam");
                assertTrue(goalConstants.contains("targetPos"),
                        goal + " lost its target-position field seam");
            }
        }
    }

    @Test
    void mustNotHappen_theBaselineGateFallsBackToAnOldOrMissingArtifact() {
        Path jar = referenceJar();
        assertTrue(Files.isRegularFile(jar));
        assertTrue(jar.getFileName().toString().contains("0.96.0"),
                "the canonical compatibility oracle must not silently fall back to v0.89");
    }

    private static Path referenceJar() {
        String configured = System.getProperty("spm.reference.jar");
        assertNotNull(configured, "Gradle must provide the pinned SPM reference path");
        return Path.of(configured);
    }

    private static InputStream requiredEntry(ZipFile zip, String path) throws Exception {
        ZipEntry entry = zip.getEntry(path);
        assertNotNull(entry, "missing artifact entry: " + path);
        return zip.getInputStream(entry);
    }

    private static String constants(ZipFile zip, String path) throws Exception {
        try (InputStream input = requiredEntry(zip, path)) {
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
