package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3RuntimeWitnessCommandsTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/V3RuntimeWitnessCommands.java");

    @Test
    void inspectorReadsSharedProductionTruth() throws IOException {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("MandatoryOwnershipRegistry.liveClaim("));
        assertTrue(source.contains("ActivityObservationService.observe("));
        assertTrue(source.contains("MandatoryOwnership.evaluate("));
        assertTrue(source.contains("VillageWorkAdmission.evaluate("));
        assertTrue(source.contains("MoveHolderClassifier.activityClass("));
    }

    @Test
    void inspectorCannotAcquireProductionAuthorityOrMutateFixtureState() throws IOException {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("MandatoryOwnershipRegistry.publish("));
        assertFalse(source.contains("MandatoryOwnershipRegistry.release("));
        assertFalse(source.contains("setProfile("));
        assertFalse(source.contains("grantOwner("));
        assertFalse(source.contains("addShare("));
        assertFalse(source.contains("getNavigation()"));
        assertFalse(source.contains("canUse()"));
        assertFalse(source.contains("canContinueToUse()"));
        assertFalse(source.contains("setItem("));
        assertFalse(source.contains("performTrade"));
    }

    @Test
    void inspectorIsOneShotWithoutRetainedSessionState() throws IOException {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("static final Map"));
        assertFalse(source.contains("ConcurrentHashMap"));
        assertFalse(source.contains("SERVER_TICK"));
        assertFalse(source.contains("UUID target"));
    }
}
