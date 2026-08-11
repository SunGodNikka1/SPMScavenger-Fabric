package com.noobk.spmscavenger.opinion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentRouteIntegrationContractTest {

    @Test
    void routeClassificationOccursAfterTickingGuardAndOnlyInScoreBlock() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        int guard = source.indexOf("chunkGuardTicking(level");
        int classify = source.indexOf("EnvironmentClassifier.classify(");
        int scoreBlock = source.indexOf("if (!forced) {", guard);

        assertTrue(guard >= 0 && classify > guard, "classification must follow existing ticking guard");
        assertTrue(classify > scoreBlock, "companion forced routes must not gain preference lookups");
        assertFalse(source.contains("setPathfindingMalus"),
                "semantic affinity must never weaken powder-snow or other terrain safety");
        assertFalse(source.contains("ProjectOpinionMemory"));
    }
}
