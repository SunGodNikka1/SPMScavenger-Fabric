package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class V4TradeLivenessWitnessTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @AfterEach
    void reset() {
        V4TradeLivenessWitness.reset();
    }

    @Test
    void classificationDistinguishesEveryTerminalBoundary() {
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_NOT_SCHEDULED,
                classify(0, 0, 0, 0, false, 0, false, false));
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_EARLY_GATE_REJECTED,
                classify(1, 0, 0, 0, false, 0, false, false));
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_MARKET_DISCOVERY_EMPTY,
                classify(1, 0, 1, 0, true, 0, false, false));
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_ADMITTED_NOT_STARTED,
                classify(1, 1, 1, 1, true, 0, true, false));
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_STARTED_NO_BOARD,
                classify(1, 1, 1, 1, true, 1, false, false));
        assertEquals(V4TradeLivenessWitness.Diagnosis.TRADE_PREEMPTED,
                classify(0, 0, 0, 0, false, 0, false, true));
        assertEquals(V4TradeLivenessWitness.Diagnosis.UNKNOWN,
                V4TradeLivenessWitness.classify(
                        new V4TradeLivenessWitness.ClassificationEvidence(
                                true, false, 0, 0, 0, 0, false, 0, false, false)));
    }

    @Test
    void repeatedDemandAndRouteStateAreCoalesced() {
        Object backpack = new Object();
        WorkDemandPolicy.MaterialDemand demand = demand();
        V4TradeLivenessWitness.arm(MOB, TRADER, backpack, 1L);
        V4TradeLivenessWitness.enterTradeCanUse(MOB, new Object(), 2L);
        V4TradeLivenessWitness.observeTradeRouteGate(MOB, demand, true, 2L);
        V4TradeLivenessWitness.observeTradeRouteGate(MOB, demand, true, 3L);
        V4TradeLivenessWitness.observeTradeRouteGate(MOB, demand, true, 4L);

        long demandEvents = V4TradeLivenessWitness.events().stream()
                .filter(event -> event.contains("event=LIVE_DEMAND"))
                .count();
        assertEquals(1L, demandEvents);
        assertTrue(V4TradeLivenessWitness.snapshot().eventCount() < 10);
    }

    @Test
    void observerHasNoAuthorityOrDiscoveryCalls() throws Exception {
        String witness = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4TradeLivenessWitness.java"));
        for (String forbidden : new String[] {
                ".canUse(", ".authorizedCandidate(", ".getEntitiesOfClass(", ".getOffers(",
                ".addGoal(", ".removeGoal(", ".removeAllGoals(",
                "RouteExhaustionEvidence.publish(", "RouteExhaustionEvidence.clear(",
                "TradeSessionClaimWindow.claim(", "TradeSessionClaimWindow.release(",
                "marketDiscoveryCooldown.clear(", "marketDiscoveryCooldown.recordEmpty("}) {
            assertFalse(witness.contains(forbidden), "passive witness leak: " + forbidden);
        }

        String queryMixin = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/mixin/"
                        + "V4EntityQueryLivenessMixin.java"));
        assertFalse(queryMixin.contains(".getEntitiesOfClass("),
                "query mixin may observe only the already-produced result");
        assertTrue(queryMixin.contains("CallbackInfoReturnable<List<T>>"));
    }

    @Test
    void productionControllerNoLongerCallsStatefulRouteStatusForDiagnostics() throws Exception {
        String controller = Files.readString(Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/"
                        + "V4RuntimeCampaignController.java"));
        assertFalse(controller.contains("ExistingRouteFeasibility.status("));
        assertTrue(controller.contains("V4TradeLivenessWitness.snapshot().routeStatus()"));
    }

    private static V4TradeLivenessWitness.Diagnosis classify(
            int canUse, int canUseTrue, int authorized, int authorizedPresent,
            boolean discovery, int starts, boolean board, boolean blocker) {
        return V4TradeLivenessWitness.classify(
                new V4TradeLivenessWitness.ClassificationEvidence(
                        true, true, canUse, canUseTrue, authorized, authorizedPresent,
                        discovery, starts, board, blocker));
    }

    private static WorkDemandPolicy.MaterialDemand demand() {
        return new WorkDemandPolicy.MaterialDemand(
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"), 3,
                ResourceLocation.fromNamespaceAndPath(
                        "spmscavenger", "iron_pickaxe_upgrade"));
    }
}
