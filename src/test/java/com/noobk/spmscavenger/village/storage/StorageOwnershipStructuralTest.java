package com.noobk.spmscavenger.village.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Task-54 — structural negatives S1–S15 (source-text gates). */
class StorageOwnershipStructuralTest {

    private static final Path STORAGE = Path.of(
            "src/main/java/com/noobk/spmscavenger/village/storage");
    private static final Path MIXIN = Path.of(
            "src/main/java/com/noobk/spmscavenger/mixin/RaidContainersAllyStorageMixin.java");
    private static final Path PER_MOB = Path.of(
            "src/main/java/com/noobk/spmscavenger/PerMobSavedData.java");

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    void s4_noVillagePerceptionObserveInRaidPolicy() throws IOException {
        String source = read(STORAGE.resolve("StorageRaidPolicy.java"));
        assertFalse(source.contains("VillagePerception.observe"),
                "hot path must not observe village perception");
    }

    @Test
    void s5_ownershipPolicyDoesNotMutateSavedData() throws IOException {
        String source = read(STORAGE.resolve("StorageOwnershipPolicy.java"));
        assertFalse(source.contains("StoragePermissionSavedData.get"),
                "diagnostics must not allocate SavedData");
        assertFalse(source.contains(".grantOwner"), "diagnostics must not mutate grants");
    }

    @Test
    void s6_mixinDeclaresCanContinueToUse() throws IOException {
        String source = read(MIXIN);
        assertTrue(source.contains("canContinueToUse"));
        assertTrue(source.contains("method_6266"));
    }

    private static String code(String java) {
        StringBuilder out = new StringBuilder(java.length());
        boolean inBlock = false;
        for (String line : java.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("/**")) {
                if (!trimmed.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Test
    void s10_raidPolicyDoesNotImportGuardCompatibility() throws IOException {
        String source = code(read(STORAGE.resolve("StorageRaidPolicy.java")));
        assertFalse(source.contains("import ") && source.contains("StorageGuardCompatibility"),
                "enforcement must not import diagnostics wiring");
    }

    @Test
    void s11_guardCompatibilityDoesNotAffectMayLoot() throws IOException {
        String raid = read(STORAGE.resolve("StorageRaidPolicy.java"));
        assertFalse(Pattern.compile("guardActive|isOperational|missingObservations")
                .matcher(raid).find(),
                "mayLoot must not branch on compatibility diagnostics");
    }

    @Test
    void s12_noMandatoryOwnershipInStorageGuard() throws IOException {
        String raid = read(STORAGE.resolve("StorageRaidPolicy.java"));
        String mixin = read(MIXIN);
        assertFalse(raid.contains("MandatoryOwnership"));
        assertFalse(mixin.contains("MandatoryOwnership"));
    }

    @Test
    void s13_storagePermissionRegisteredInPerMobForgetAll() throws IOException {
        String source = read(PER_MOB);
        assertTrue(source.contains("StoragePermissionSavedData"),
                "grant store must participate in PerMobSavedData.forgetAll");
    }

    @Test
    void s14_settlementFactIsTriStateEnum() throws IOException {
        String source = read(STORAGE.resolve("SettlementStorageFact.java"));
        assertTrue(source.contains("IN_KNOWN_SETTLEMENT"));
        assertTrue(source.contains("OUTSIDE_KNOWN_SETTLEMENT"));
        assertTrue(source.contains("UNKNOWN"));
        assertFalse(source.contains("boolean withinSettlement"),
                "must not collapse to boolean settlement fact");
    }

    @Test
    void s3_noBlockEntitySetRemovedRemovalReason() throws IOException {
        String lifecycle = read(STORAGE.resolve("StorageGrantLifecycle.java"));
        assertFalse(lifecycle.contains("setRemoved(RemovalReason"),
                "1.21.1 BlockEntity has no RemovalReason API");
    }

    @Test
    void lifecycleUsesLogicalIdentityPredicate() throws IOException {
        String lifecycle = read(STORAGE.resolve("StorageGrantLifecycle.java"));
        String identity = read(STORAGE.resolve("StorageLogicalIdentity.java"));
        assertTrue(lifecycle.contains("StorageLogicalIdentity.logicalIdentityChanged"));
        assertTrue(identity.contains("getConnectedDirection"));
        assertFalse(lifecycle.contains("invalidateOldDoublePartner"));
    }

    @Test
    void resolverUsesConnectedDirectionNotGetConnectedBlockPos() throws IOException {
        String source = read(STORAGE.resolve("StorageContainerResolver.java"));
        assertTrue(source.contains("getConnectedDirection"));
        assertFalse(source.contains("getConnectedBlockPos"));
    }
}
