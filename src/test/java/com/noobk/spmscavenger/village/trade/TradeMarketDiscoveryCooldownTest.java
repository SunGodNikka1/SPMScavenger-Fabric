package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeMarketDiscoveryCooldownTest {

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");
    private static final ResourceLocation IRON =
            ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot");
    private static final ResourceLocation COAL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "coal");

    @Test
    void mustHappen_completedEmptyDiscoverySuppressesOnlyItsBoundedRetryWindow() {
        TradeMarketDiscoveryCooldown cooldown = new TradeMarketDiscoveryCooldown();
        cooldown.recordEmpty(CONSUMER, IRON, 1_000L);

        assertTrue(cooldown.coolingDown(CONSUMER, IRON, 1_000L));
        assertTrue(cooldown.coolingDown(CONSUMER, IRON,
                1_000L + TradeMarketDiscoveryCooldown.EMPTY_SCAN_COOLDOWN_TICKS - 1L));
        assertFalse(cooldown.coolingDown(CONSUMER, IRON,
                1_000L + TradeMarketDiscoveryCooldown.EMPTY_SCAN_COOLDOWN_TICKS));
    }

    @Test
    void mustNotHappen_emptyEvidenceForOneDemandSuppressesAnotherDemand() {
        TradeMarketDiscoveryCooldown cooldown = new TradeMarketDiscoveryCooldown();
        cooldown.recordEmpty(CONSUMER, IRON, 1_000L);

        assertFalse(cooldown.coolingDown(CONSUMER, COAL, 1_001L));
        assertFalse(cooldown.coolingDown(
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "other_consumer"),
                IRON, 1_001L));
    }

    @Test
    void mustHappen_successClearsPriorEmptyEvidence() {
        TradeMarketDiscoveryCooldown cooldown = new TradeMarketDiscoveryCooldown();
        cooldown.recordEmpty(CONSUMER, IRON, 1_000L);
        cooldown.clear();

        assertFalse(cooldown.coolingDown(CONSUMER, IRON, 1_001L));
    }
}
