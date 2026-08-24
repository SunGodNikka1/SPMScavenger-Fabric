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
    private static final Path SNAPSHOT_SOURCE = Path.of(
            "src/main/java/com/noobk/spmscavenger/debug/V3WitnessSnapshot.java");

    @Test
    void inspectorReadsSharedProductionTruth() throws IOException {
        String source = Files.readString(SNAPSHOT_SOURCE);
        assertTrue(source.contains("MandatoryOwnershipRegistry.liveClaim("));
        assertTrue(source.contains("ActivityObservationService.observe("));
        assertTrue(source.contains("MandatoryOwnership.evaluate("));
        assertTrue(source.contains("VillageWorkAdmission.evaluate("));
        assertTrue(source.contains("MoveHolderClassifier.activityClass("));
        assertTrue(source.contains("VillageMemorySavedData.peekInDimension("));
        assertTrue(source.contains("memory.peek(mob.getUUID())"));
        assertTrue(source.contains("VillageWorkFactsService.peekReadOnly("));
        assertTrue(source.contains("Gate0="));
        assertTrue(source.contains("settlement observed: "));
        assertTrue(source.contains("settlement anchor: "));
        assertTrue(source.contains("adultVillagerCount="));
        assertTrue(source.contains("totalUsableHomeCapacity="));
        assertTrue(source.contains("claimedHomeCount="));
        assertTrue(source.contains("currentFreeHomeCapacity="));
        assertTrue(source.contains("completeness="));
        assertTrue(source.contains("freshness="));
        assertTrue(source.contains("ActivityClass.SHELTER_HOLD"));
        assertTrue(source.contains("level.isDay()"));
        assertTrue(source.contains("RowPrecondition="));
        String commands = Files.readString(SOURCE);
        assertTrue(commands.contains("Commands.literal(\"run\")"));
        assertTrue(commands.contains("Commands.literal(\"report\")"));
    }

    @Test
    void inspectorCannotAcquireProductionAuthorityOrMutateFixtureState() throws IOException {
        String source = Files.readString(SNAPSHOT_SOURCE);
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
        assertFalse(source.contains("VillageMemorySavedData.get("));
        assertFalse(source.contains("memoryOf("));
        assertFalse(source.contains(".remember("));
        assertFalse(source.contains("VillageWorkFactsService.peek("));
        assertFalse(source.contains("refreshNow("));
        assertFalse(source.contains("scheduleFor"));
        assertFalse(source.contains("requestRefresh("));
        assertFalse(source.contains("acquireTicket("));
        assertFalse(source.contains("PoiManager"));
        assertFalse(source.contains("Brain"));
        assertFalse(source.contains("SleepingX"));
        assertFalse(source.contains("setDirty("));
        assertFalse(source.contains("setDayTime("));
        assertFalse(source.contains("setTime("));
        assertFalse(source.contains("wrapped.stop()"));
    }

    @Test
    void inspectorIsOneShotWithoutRetainedSessionState() throws IOException {
        String source = Files.readString(SNAPSHOT_SOURCE);
        assertFalse(source.contains("static final Map"));
        assertFalse(source.contains("ConcurrentHashMap"));
        assertFalse(source.contains("SERVER_TICK"));
        assertFalse(source.contains("static UUID"));
    }
}
