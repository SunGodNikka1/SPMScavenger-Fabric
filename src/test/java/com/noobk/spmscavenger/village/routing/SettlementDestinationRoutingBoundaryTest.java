package com.noobk.spmscavenger.village.routing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementDestinationRoutingBoundaryTest {

    @Test
    void rankingPackageOwnsNoWorldPathScanOrAuthoritySurface() throws IOException {
        String source = routingSource();
        for (String forbidden : new String[] {
                "ServerLevel", "createPath", "moveTo(", "getChunk", "hasChunk",
                "VillagePerception", "getEntities", "Villager.class", "MerchantOffer",
                "MandatoryOwnership", "VillageIntent", "ExploringGoal", "COMMUTE"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("SettlementOpinionBias.request("));
        assertTrue(source.contains("CapabilityEvidenceClass"));
        assertTrue(source.contains("FactualVillageUtility"));
    }

    @Test
    void attemptEvidenceIsTransientAndHasNoPersistenceOrFailureProducer() throws IOException {
        String evidence = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/routing/RouteAttemptEvidence.java"));
        assertFalse(evidence.contains("CompoundTag"));
        assertFalse(evidence.contains("SavedData"));
        assertFalse(evidence.contains("recordFailure"));
        assertFalse(evidence.contains("pathFailure"));
        assertFalse(evidence.contains("save("));
        assertFalse(evidence.contains("load("));
    }

    @Test
    void noV4eOrLaterImplementationWasAdded() throws IOException {
        String all = allProductionSource();
        assertFalse(all.contains("class VillageInteractionDirector"));
        assertFalse(all.contains("FirstHomePromotion"));
    }

    private static String routingSource() throws IOException {
        Path root = Path.of("src/main/java/com/noobk/spmscavenger/village/routing");
        StringBuilder combined = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                combined.append(Files.readString(file));
            }
        }
        return combined.toString();
    }

    private static String allProductionSource() throws IOException {
        Path root = Path.of("src/main/java/com/noobk/spmscavenger");
        StringBuilder combined = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                combined.append(Files.readString(file));
            }
        }
        return combined.toString();
    }
}
