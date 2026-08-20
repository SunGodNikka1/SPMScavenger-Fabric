package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;

/**
 * Retry control for a completed market discovery that produced no authorized trade.
 *
 * <p>This is intentionally separate from {@link TradeCandidateRound}. A candidate round cools down
 * after physical attempts have been exhausted; this state suppresses repeated offer/quote scans
 * when no attempt could begin. It is keyed to both consumer and material so an unrelated newly
 * selected demand is never delayed by stale negative market evidence.
 *
 * <p>RET-1: one fixed-size object per trade goal, no static registry and no entity/world reference.
 */
public final class TradeMarketDiscoveryCooldown {

    /** Independent policy value; equality with the candidate-round cooldown is not coupling. */
    public static final long EMPTY_SCAN_COOLDOWN_TICKS = 200L;

    private ResourceLocation consumerKey;
    private ResourceLocation materialKey;
    private long retryAtTick = Long.MIN_VALUE;

    public boolean coolingDown(
            ResourceLocation consumerKey, ResourceLocation materialKey, long gameTime) {
        return this.consumerKey != null
                && this.consumerKey.equals(consumerKey)
                && this.materialKey.equals(materialKey)
                && gameTime < retryAtTick;
    }

    public void recordEmpty(
            ResourceLocation consumerKey, ResourceLocation materialKey, long gameTime) {
        this.consumerKey = consumerKey;
        this.materialKey = materialKey;
        retryAtTick = gameTime + EMPTY_SCAN_COOLDOWN_TICKS;
    }

    public void clear() {
        consumerKey = null;
        materialKey = null;
        retryAtTick = Long.MIN_VALUE;
    }
}
