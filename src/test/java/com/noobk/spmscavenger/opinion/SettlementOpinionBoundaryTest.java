package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementOpinionBoundaryTest {

    @Test
    void facadeIsIntegerPreferenceOnlyAndOwnsNoPersistenceOrWorldAccess() throws IOException {
        String bias = source("opinion/SettlementOpinionBias.java");
        String context = source("opinion/SettlementOpinionContext.java");

        assertTrue(bias.contains("public static int request("));
        assertFalse(bias.contains("ServerLevel"));
        assertFalse(bias.contains("MerchantOffer"));
        assertFalse(bias.contains("Permission"));
        assertFalse(bias.contains("Eligibility"));
        assertFalse(bias.contains("canUse("));
        assertFalse(bias.contains("affectiveState()"));
        assertFalse(bias.contains("opinionMemory()"));
        assertFalse(bias.contains("sociability()"));
        assertFalse(bias.contains("subjectPreference()"));
        assertFalse(bias.contains("settlementSocialBias()"));
        assertFalse(context.contains("CompoundTag"));
        assertFalse(context.contains("BlockPos"));
        assertFalse(context.contains("ChunkPos"));
        assertFalse(context.contains("placeOpinionChunkKey"));
        assertFalse(context.contains("MobExperienceContext"));
    }

    @Test
    void contextIsAValueSnapshotAndNotASettlementOpinionStore() throws IOException {
        String context = source("opinion/SettlementOpinionContext.java");
        assertTrue(context.contains("Map.copyOf"));
        assertTrue(context.contains("captureSnapshot()"));
        assertFalse(context.contains("recordOutcome("));
        assertFalse(context.contains("restoreFromSnapshot("));
        assertFalse(context.contains("SETTLEMENT"));
        assertFalse(context.contains("save("));
        assertFalse(context.contains("load("));
    }

    @Test
    void futureVillageDestinationOwnersCannotComposePersonalityOrAffectDirectly()
            throws IOException {
        Path villageRoot = Path.of("src/main/java/com/noobk/spmscavenger/village");
        List<String> destinationOwnerNames = List.of(
                "VillageInteractionDirector", "VillageDestination",
                "SettlementDestinationRanker");
        try (var paths = Files.walk(villageRoot)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (destinationOwnerNames.stream().noneMatch(name::contains)) {
                    continue;
                }
                String body = Files.readString(file);
                assertFalse(body.contains("PersonalityModel"), file.toString());
                assertFalse(body.contains("AffectiveState"), file.toString());
                assertTrue(body.contains("SettlementOpinionBias")
                                || body.contains("SettlementOpinionInputs"),
                        file.toString());
            }
        }
    }

    @Test
    void v4eDirectorAndTheSingleAuthorizedV4fHomeOwnerExist() throws IOException {
        Path root = Path.of("src/main/java/com/noobk/spmscavenger");
        try (var paths = Files.walk(root)) {
            List<String> names = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertEquals(1, names.stream().filter(name -> name.equals("VillageInteractionDirector.java")).count());
            assertEquals(1, names.stream().filter(name -> name.equals("FirstHomePromotion.java")).count());
        }
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }
}
