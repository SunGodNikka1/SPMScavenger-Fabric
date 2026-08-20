package com.noobk.spmscavenger.village.trade;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural negative controls for the hot-path market-discovery boundary. */
class TradeMarketDiscoveryWiringTest {

    private static String goalSource() throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"));
    }

    private static String methodOf(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("missing method: " + signature);
        }
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("unterminated method: " + signature);
    }

    @Test
    void mustHappen_routeAuthorityPrecedesEveryMarketOperation() throws IOException {
        String discovery = methodOf(goalSource(),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");
        int authority = discovery.indexOf("existingRouteInfeasible(level, demand.get())");
        int entityQuery = discovery.indexOf("level.getEntitiesOfClass(");
        int offerQuery = discovery.indexOf("source.offers(villager, query)");

        assertTrue(authority > 0 && authority < entityQuery && entityQuery < offerQuery,
                "existing-route authority must reject trading before entity, offer or quote work");
    }

    @Test
    void mustHappen_q1AttemptMovesFromAdmissionIntoStartWithoutRediscovery() throws IOException {
        String source = goalSource();
        String canUse = methodOf(source, "public boolean canUse()");
        String start = methodOf(source, "public void start()");

        assertTrue(canUse.contains("pendingAttempt = admitted.get()"),
                "Q1 must retain the exact authorized attempt across the GoalSelector transition");
        assertTrue(start.contains("AuthorizedAttempt admitted = pendingAttempt")
                        && start.contains("beginAttempt(level, admitted)"),
                "start must consume the admitted attempt");
        assertFalse(start.contains("authorizedCandidate("),
                "start must not repeat villager discovery, offer snapshots or optional-source quotes");
    }

    @Test
    void mustNotHappen_permissionAbsenceCreatesEmptyMarketEvidence() throws IOException {
        String canUse = methodOf(goalSource(), "public boolean canUse()");
        int demand = canUse.indexOf("Optional<WorkDemandPolicy.MaterialDemand> demand");
        int route = canUse.indexOf("existingRouteInfeasible(level, demand.get())");
        int backpack = canUse.indexOf("PlayerMobs.backpack(mob)");
        int discovery = canUse.indexOf("authorizedCandidate(level, null)");
        int emptyEvidence = canUse.indexOf("marketDiscoveryCooldown.recordEmpty(");

        assertTrue(demand > 0 && demand < route && route < backpack
                        && backpack < discovery && discovery < emptyEvidence,
                "empty-scan cooldown may be recorded only after permission and a completed scan");
    }
}
