package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3HarvestEpisodeProbeTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/V3HarvestEpisodeProbe.java");

    @Test
    void probeOnlyReadsInstalledGoalStateAndFailsUnavailable() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("getAvailableGoals()"));
        assertTrue(source.contains("wrapped.isRunning()"));
        assertTrue(source.contains("field(\"phase\")"));
        assertTrue(source.contains("field(\"targetPos\")"));
        assertTrue(source.contains("getDeclaredField(name)"));
        assertTrue(source.contains("\"UNAVAILABLE\""));
        assertFalse(source.contains("goal.canUse("));
        assertFalse(source.contains("goal.canContinueToUse("));
        assertFalse(source.contains("goal.start("));
        assertFalse(source.contains("goal.tick("));
        assertFalse(source.contains("goal.stop("));
        assertFalse(source.contains("setAccessible(false)"));
    }
}
