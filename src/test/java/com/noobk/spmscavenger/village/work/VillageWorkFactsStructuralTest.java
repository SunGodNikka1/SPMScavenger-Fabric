package com.noobk.spmscavenger.village.work;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Task-56 — structural boundaries for V3-D work facts. */
class VillageWorkFactsStructuralTest {

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }

    @Test
    void mustHappen_admissionDoesNotReadWorkFacts() throws IOException {
        String body = source("village/VillageWorkAdmission.java");
        assertFalse(body.contains("VillageWorkFacts"),
                "VillageWorkAdmission must not read VillageWorkFacts");
        assertFalse(body.contains("village.work"),
                "VillageWorkAdmission must not import village.work");
    }

    @Test
    void mustHappen_knownVillageDoesNotPersistPopulationCounts() throws IOException {
        String body = source("village/KnownVillage.java");
        assertFalse(body.contains("adultVillagerCount"));
        assertFalse(body.contains("currentFreeHomeCapacity"));
        assertFalse(body.contains("eligibleBedCount"));
        assertFalse(body.contains("freePopulationCapacity"));
    }

    @Test
    void mustHappen_observationKernelUsesBoundedEnumerationSeams() throws IOException {
        String body = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/work/VillageWorkObservationKernel.java"));
        assertFalse(body.contains(".toList()"),
                "HOME POI query must stay lazy — no materializing toList()");
        assertTrue(body.contains("MAX_VILLAGERS_PER_OBSERVATION + 1"),
                "villager query must use bounded ServerLevel#getEntities maxResults seam");
        assertTrue(body.contains("HomePoiCandidateSource"),
                "HOME enumeration must be injectable for budget tests");
    }

    @Test
    void mustNotHappen_productionUsesSubtractionFormula() throws IOException {
        Path root = Path.of("src/main/java/com/noobk/spmscavenger/village/work");
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(file);
                assertFalse(body.contains("eligibleBedCount"),
                        file.getFileName() + " must not use removed eligibleBedCount term");
                assertFalse(body.contains("freePopulationCapacity"),
                        file.getFileName() + " must not use removed subtraction authority");
            }
        }
    }
}
