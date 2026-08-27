package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GeneralDebugCommandsTest {

    private static final Path SNAPSHOT = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/GeneralDebugSnapshot.java");
    private static final Path COMMAND = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/GeneralDebugCommands.java");

    @Test
    void inspectorUsesOnlyPassiveNonCreatingTruthProviders() throws IOException {
        String source = Files.readString(SNAPSHOT);
        assertTrue(source.contains("MiningProjectSavedData.peekReadOnly("));
        assertTrue(source.contains("MandatoryOwnershipRegistry.peekLiveClaim("));
        assertTrue(source.contains("VillageMemorySavedData.peekInDimension("));
        assertTrue(source.contains("VillageWorkFactsService.peekReadOnly("));
        assertTrue(source.contains("PlayerMobVillagePolicySavedData.profileOf("));
        assertFalse(source.contains("MiningProjectSavedData.get("));
        assertFalse(source.contains("MandatoryOwnershipRegistry.liveClaim("));
        assertFalse(source.contains("VillageMemorySavedData.get("));
        assertFalse(source.contains("VillageWorkFactsService.peek("));
        assertFalse(source.contains("refreshNow("));
        assertFalse(source.contains("setDirty("));
        assertFalse(source.contains("setItem("));
        assertFalse(source.contains("getNavigation("));
        assertFalse(source.contains("canUse("));
        assertFalse(source.contains("performTrade("));
    }

    @Test
    void commandIsOneShotInspectOnly() throws IOException {
        String source = Files.readString(COMMAND);
        assertTrue(source.contains("Commands.literal(\"inspect\")"));
        assertFalse(source.contains("Commands.literal(\"run\")"));
        assertFalse(source.contains("Commands.literal(\"report\")"));
        assertFalse(source.contains("Commands.literal(\"reset\")"));
        assertFalse(source.contains("SERVER_TICK"));
        assertFalse(source.contains("static UUID"));
    }
}
