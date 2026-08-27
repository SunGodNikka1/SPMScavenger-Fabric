package com.noobk.spmscavenger.goal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V4-A structural boundary: observe an existing board; never create a second market authority. */
class KnownVillagerObservationContractTest {

    @Test
    void observationPiggybacksOnVanillaSourceAndItsResultCannotAffectPlanning() throws Exception {
        String goal = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"));
        int sourceRead = goal.indexOf("List<OfferSnapshot> sourceOffers = source.offers(villager, query)");
        int observation = goal.indexOf("KnownTraderMarketObservation.recordVanillaBoard");
        int funding = goal.indexOf("fundingTarget(purchaseDemand, offers, backpack)", observation);

        assertTrue(sourceRead >= 0 && observation > sourceRead,
                "observation must consume the board V2 already read");
        assertTrue(goal.substring(sourceRead, observation).contains("TradeSourceKey.VANILLA"),
                "query-shaped synthetic quotes must not be persisted as a complete board");
        assertTrue(funding > observation,
                "memory observation occurs before policy only as a discarded passive side effect");
        assertFalse(goal.substring(observation, funding).contains("if (KnownTraderMarketObservation"));
        assertFalse(goal.substring(observation, funding).contains("= KnownTraderMarketObservation"));
    }

    @Test
    void passiveBridgeDoesNotRescanOrAcquireMarketAuthority() throws Exception {
        String bridge = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/village/KnownTraderMarketObservation.java"));
        assertFalse(bridge.contains("getOffers("));
        assertFalse(bridge.contains("TradeDemandGate"));
        assertFalse(bridge.contains("TradeEvaluationPolicy"));
        assertFalse(bridge.contains("performTrade"));
        assertFalse(bridge.contains("revalidate"));
        assertFalse(bridge.contains("MerchantOffer"));
        assertTrue(bridge.contains("peekInDimension"), "read must not create village SavedData");
    }
}
