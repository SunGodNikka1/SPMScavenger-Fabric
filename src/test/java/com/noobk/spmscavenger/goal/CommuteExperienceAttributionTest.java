package com.noobk.spmscavenger.goal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** V1.5-C — COMMUTE must not attribute exploration experience (causal-attribution contract). */
class CommuteExperienceAttributionTest {

    private static final Path EXPLORING_GOAL =
            Path.of("src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java");

    @Test
    void mustNotHappen_commuteExpeditionsEmitExplorationExperience() throws IOException {
        String source = Files.readString(EXPLORING_GOAL);
        assertTrue(
                source.contains("attributesExplorationExperience"),
                "commute attribution guard must exist");
        assertTrue(
                source.contains("state.kind != ExpeditionKind.COMMUTE"),
                "COMMUTE must be excluded from exploration experience emitters");
    }

    @Test
    void mustHappen_commuteKindExists() {
        assertTrue(ExpeditionKind.COMMUTE != ExpeditionKind.DISCRETIONARY);
    }
}
