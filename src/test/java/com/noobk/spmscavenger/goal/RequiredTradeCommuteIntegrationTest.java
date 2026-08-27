package com.noobk.spmscavenger.goal;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredTradeCommuteIntegrationTest {

    @Test
    void requiredTradeArrivalUsesAnchorScaleNotV15PresenceBoundary() {
        BlockPos anchor = new BlockPos(100, 64, 100);
        assertEquals(25.0, ExploringGoal.horizontalDistanceSqr(
                new BlockPos(105, 90, 100), anchor));
        assertEquals(4225.0, ExploringGoal.horizontalDistanceSqr(
                new BlockPos(165, -40, 100), anchor));
    }

    @Test
    void v15ReturnPolicyAndRequiredTradeExactFinalAnchorBothRemain() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        assertTrue(source.contains("SettlementReturnPolicy.shouldStartCommute"));
        assertTrue(source.contains("SettlementReturnPolicy.shouldContinueCommute"));
        assertTrue(source.contains("!SettlementBoundsPolicy.within(actualEnd, commuteAnchor)"));
        assertTrue(source.contains("x = anchor.getX();"));
        assertTrue(source.contains("z = anchor.getZ();"));
        assertTrue(source.contains("seedRequiredTradeCommuteExpedition"));
    }

    @Test
    void bindingLivesOnlyInDurableExpeditionAndNeverDisposableNavigation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        int expedition = source.indexOf("private static final class ExpeditionState");
        int navigation = source.indexOf("private static final class NavigationState");
        assertTrue(source.substring(expedition, navigation).contains("requiredTradeBinding"));
        assertFalse(source.substring(navigation).contains("requiredTradeBinding"));

        int stop = source.indexOf("public void stop()");
        int next = source.indexOf("@Override", stop + 20);
        String stopBody = source.substring(stop, next);
        assertTrue(stopBody.contains("navigationState = null"));
        assertFalse(stopBody.contains("expedition = null"));
    }

    @Test
    void arrivalReleasesTravelOwnershipWithoutMarketWork() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/noobk/spmscavenger/goal/ExploringGoal.java"));
        assertTrue(source.contains("VillageInteractionDirector.completeArrival"));
        assertFalse(source.contains("MerchantOffer"));
        assertFalse(source.contains("getOffers("));
        assertFalse(source.contains("TradeWithVillagerGoal"));
    }
}
