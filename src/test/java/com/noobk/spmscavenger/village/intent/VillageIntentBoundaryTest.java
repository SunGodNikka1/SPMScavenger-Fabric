package com.noobk.spmscavenger.village.intent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageIntentBoundaryTest {

    @Test
    void payloadAndOwnerContainNoMarketNavigationOrPersistenceAuthority() throws IOException {
        String source = intentSource();
        for (String forbidden : new String[] {
                "MerchantOffer", "OfferSnapshot", "TradeEvaluation", "price", "affordability",
                "Navigation", "Path ", "PathNavigation", "waypoint", "createPath", "moveTo(",
                "CompoundTag", "SavedData", "VillageMemorySavedData", "RouteAttemptEvidence",
                "recordFailure", "MandatoryOwnershipRegistry", "TradeDemandGate.Authorization",
                "opinionBias", "factualUtility", "capabilityEvidence", "MerchantOffer",
                "VillageInteractionDirector", "ExploringGoal", "COMMUTE"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("MaterialDemandIdentity"));
        assertTrue(source.contains("ExistingRouteStatus.INFEASIBLE"));
    }

    @Test
    void productionLifecycleReleasesTransientIntent() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/SpmScavenger.java"));
        assertTrue(count(source, "VillageInteractionDirector.release(") >= 2,
                "entity unload and death must release intent and attempt history together");
        assertTrue(source.contains("VillageInteractionDirector\n                            .shutdownServerState()"));
    }

    @Test
    void v4EAddsFacadeButNoSecondGoalOrFirstHomeBehavior() throws IOException {
        String source = allProductionSource();
        assertTrue(source.contains("class VillageInteractionDirector"));
        assertFalse(source.contains("FirstHomePromotion"));
        assertFalse(source.contains("VillageIntentGoal"));
        assertFalse(source.contains("VillageTravelGoal"));
        assertFalse(source.contains("RequiredTradeTravelGoal"));
        assertFalse(intentSource().contains("RouteAttemptEvidence.Attempt"));
    }

    private static String intentSource() throws IOException {
        return sourceUnder(Path.of("src/main/java/com/noobk/spmscavenger/village/intent"));
    }

    private static String allProductionSource() throws IOException {
        return sourceUnder(Path.of("src/main/java/com/noobk/spmscavenger"));
    }

    private static String sourceUnder(Path root) throws IOException {
        StringBuilder combined = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                combined.append(Files.readString(file));
            }
        }
        return combined.toString();
    }

    private static int count(String body, String needle) {
        return body.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
