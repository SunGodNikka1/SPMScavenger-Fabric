package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3Gate0ReadOnlyWiringTest {

    @Test
    void serviceReadOnlyPathDoesNotCreateOrWriteCache() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/work/VillageWorkFactsService.java"));
        String method = between(
                source,
                "public static Optional<VillageWorkFacts> peekReadOnly(",
                "public static int drainBudget(");
        assertTrue(method.contains("VillageWorkFactsCache.peekForServer("));
        assertTrue(method.contains("cache.peekReadOnly("));
        assertFalse(method.contains("VillageWorkFactsCache.forServer("));
        assertFalse(method.contains("refreshNow("));
        assertFalse(method.contains("requestRefresh("));
        assertFalse(method.contains(".put("));
        assertFalse(method.contains(".invalidate("));
    }

    @Test
    void cacheReadOnlyPathProjectsFreshnessWithoutReplacingEntry() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/work/VillageWorkFactsCache.java"));
        String method = between(
                source,
                "public Optional<VillageWorkFacts> peekReadOnly(",
                "public void put(");
        assertTrue(method.contains("FreshnessPolicy.apply("));
        assertFalse(method.contains("entries.put("));
        assertFalse(method.contains("entries.remove("));
        assertFalse(method.contains("computeIfAbsent("));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0, () -> "missing start marker: " + start);
        assertTrue(to > from, () -> "missing end marker: " + end);
        return source.substring(from, to);
    }
}
