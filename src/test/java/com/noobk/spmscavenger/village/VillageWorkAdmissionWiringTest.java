package com.noobk.spmscavenger.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Task-53 — structural negatives S1–S12 (source-shape contracts). */
class VillageWorkAdmissionWiringTest {

    private static String source(String relative) throws IOException {
        String raw = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
        StringBuilder out = new StringBuilder(raw.length());
        boolean inBlock = false;
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** S1 — admission delegates to MandatoryOwnership.evaluate. */
    @Test
    void s1_admissionDelegatesToMandatoryOwnership() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertTrue(body.contains("MandatoryOwnership.evaluate"),
                "VillageWorkAdmission must consume MandatoryOwnership.evaluate");
    }

    /** S2 — admission must not inspect WorkDemandPolicy. */
    @Test
    void s2_admissionDoesNotInspectWorkDemandPolicy() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertFalse(body.contains("WorkDemandPolicy"),
                "VillageWorkAdmission must not inspect WorkDemandPolicy");
    }

    /** S3 — admission must not call MandatoryOwnershipRegistry directly. */
    @Test
    void s3_admissionDoesNotCallRegistry() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertFalse(body.contains("MandatoryOwnershipRegistry"),
                "VillageWorkAdmission must not call MandatoryOwnershipRegistry");
    }

    /** S4 — admission must not read settlement relationship or village memory. */
    @Test
    void s4_admissionDoesNotReadSettlementState() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertFalse(body.contains("SettlementRelationship"),
                "VillageWorkAdmission must not read SettlementRelationship");
        assertFalse(body.contains("MobVillageMemory"),
                "VillageWorkAdmission must not read MobVillageMemory");
        assertFalse(body.contains("VillageMemorySavedData"),
                "VillageWorkAdmission must not read VillageMemorySavedData");
    }

    /** S5 — profile is not stored in MobVillageMemory. */
    @Test
    void s5_profileNotInMobVillageMemory() throws IOException {
        String memory = source("village/MobVillageMemory.java");
        assertFalse(memory.contains("VillageScenarioProfile"),
                "profile must not live in MobVillageMemory");
    }

    /** S6 — ordinary ENTITY_UNLOAD must not delete policy rows. */
    @Test
    void s6_unloadHandlerDoesNotTouchPolicyStore() throws IOException {
        String bootstrap = source("SpmScavenger.java");
        assertFalse(bootstrap.contains("PlayerMobVillagePolicySavedData"),
                "ENTITY_UNLOAD must not call the policy store");
    }

    /** S7 — read/forget paths must not use computeIfAbsent on the policy store. */
    @Test
    void s7_peekAndForgetDoNotMaterialize() throws IOException {
        String body = source("village/PlayerMobVillagePolicySavedData.java");
        String profileOf = methodBody(body, "static VillageScenarioProfile profileOf(");
        String forget = methodBody(body, "static boolean forget(");
        String peek = methodBody(body, "static PlayerMobVillagePolicySavedData peek(");
        assertFalse(profileOf.contains("computeIfAbsent"),
                "profileOf must not materialize the store");
        assertFalse(forget.contains("computeIfAbsent"),
                "forget must not materialize the store");
        assertTrue(peek.contains(".get("),
                "peek must use non-creating get");
        assertFalse(peek.contains("computeIfAbsent"),
                "peek must not use computeIfAbsent");
    }

    /** S8 — policy store registered in PerMobSavedData.forgetAll. */
    @Test
    void s8_registeredInPerMobSavedData() throws IOException {
        String rule = source("PerMobSavedData.java");
        assertTrue(rule.contains("PlayerMobVillagePolicySavedData"),
                "policy store must register in forgetAll");
    }

    /** S9 — no MAX_TRACKED_MOBS-style silent eviction on policy store. */
    @Test
    void s9_noSilentCapEviction() throws IOException {
        String body = source("village/PlayerMobVillagePolicySavedData.java");
        assertFalse(body.contains("MAX_TRACKED_MOBS"),
                "policy store must not copy village-memory cap eviction");
        assertFalse(body.contains("prune("),
                "policy store must not silently prune live assignments");
    }

    /** S12 — no ServerLevel accessor that calls that level's getDataStorage(). */
    @Test
    void s12_noDimensionLocalDataStorage() throws IOException {
        String body = source("village/PlayerMobVillagePolicySavedData.java");
        assertTrue(body.contains("server.overworld()"),
                "canonical host must be overworld");
        assertFalse(body.matches("(?s).*ServerLevel.*getDataStorage\\(\\).*"),
                "must not call a dimension-local getDataStorage()");
    }

    /** Commands require operator permission and PlayerMob guard. */
    @Test
    void commandsRequireOperatorAndPlayerMob() throws IOException {
        String body = source("command/VillageProfileCommands.java");
        assertTrue(body.contains("hasPermission(2)"),
                "profile commands must require operator permission");
        assertTrue(body.contains("PlayerMobs.isPlayerMob"),
                "profile commands must reject non-PlayerMobs");
    }

    private static String methodBody(String source, String header) {
        int start = source.indexOf(header);
        if (start < 0) {
            return "";
        }
        int brace = source.indexOf('{', start);
        if (brace < 0) {
            return "";
        }
        int depth = 1;
        for (int i = brace + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        return source.substring(start);
    }
}
