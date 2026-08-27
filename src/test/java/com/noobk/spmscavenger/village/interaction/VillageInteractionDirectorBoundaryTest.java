package com.noobk.spmscavenger.village.interaction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageInteractionDirectorBoundaryTest {

    @Test
    void directorOwnsCompositionAndExploringGoalConsumesOnlyDirectiveBoundary() throws IOException {
        String director = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/interaction/"
                        + "VillageInteractionDirector.java"));
        String exploring = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));

        for (String required : new String[] {
                "WorkDemandPolicy.select", "ExistingRouteFeasibility.status",
                "VillageMemorySavedData", "SettlementDestinationRanker.select",
                "SettlementOpinionContext", "VillageIntentRegistry.revalidate"
        }) {
            assertTrue(director.contains(required), required);
        }
        for (String forbidden : new String[] {
                "SettlementDestinationRanker", "SettlementOpinionBias", "KnownVillager",
                "TradeOutputCapability", "VillageIntentRegistry.current", "MerchantOffer",
                "OfferSnapshot"
        }) {
            assertFalse(exploring.contains(forbidden), forbidden);
        }
        assertTrue(exploring.contains("VillageInteractionDirector"));
        assertTrue(exploring.contains("CommuteDirective"));
    }

    @Test
    void currentReadIsPostRevalidationConsumptionNeverMovementAuthority() throws IOException {
        String director = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/interaction/"
                        + "VillageInteractionDirector.java"));
        int boundMethod = director.indexOf("revalidateRequiredTrade(");
        int revalidate = director.indexOf("VillageIntentRegistry.revalidate", boundMethod);
        int current = director.indexOf("VillageIntentRegistry.current", revalidate);
        assertTrue(boundMethod >= 0 && revalidate > boundMethod && current > revalidate);
    }

    @Test
    void noSecondGoalNavigationStateRetryMachineOrMarketExecutorExists() throws IOException {
        String production = sourceUnder(Path.of("src/main/java/com/noobk/spmscavenger"));
        assertFalse(production.contains("class VillageTravelGoal"));
        assertFalse(production.contains("class RequiredTradeTravelGoal"));
        assertFalse(production.contains("class VillageNavigationState"));
        assertFalse(production.contains("class RequiredTradeRetry"));

        String interaction = sourceUnder(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/interaction"));
        for (String forbidden : new String[] {
                "MerchantOffer", "OfferSnapshot", "getOffers(", "performBuy", "performSell",
                "createPath(", "moveTo("
        }) {
            assertFalse(interaction.contains(forbidden), forbidden);
        }
    }

    @Test
    void failurePublicationIsTerminalPathOnlyAndStopIsPassive() throws IOException {
        String exploring = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        int abandon = exploring.indexOf("private void abandon(EndReason reason");
        int failure = exploring.indexOf("recordTerminalRouteFailure", abandon);
        int terminalGuard = exploring.lastIndexOf(
                "reason == EndReason.PATH_FAILURE", failure);
        assertTrue(abandon >= 0 && terminalGuard > abandon && failure > terminalGuard);

        int stop = exploring.indexOf("public void stop()");
        int nextMethod = exploring.indexOf("@Override", stop + 20);
        String stopBody = exploring.substring(stop, nextMethod);
        assertFalse(stopBody.contains("recordTerminalRouteFailure"));
        assertFalse(stopBody.contains("RouteAttemptEvidence"));
    }

    private static String sourceUnder(Path root) throws IOException {
        StringBuilder combined = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                combined.append(Files.readString(file)).append('\n');
            }
        }
        return combined.toString();
    }
}
