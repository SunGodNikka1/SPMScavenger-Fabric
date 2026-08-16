package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * V2-D — a transient economic intention: buy a target, selling only as much as that purchase needs.
 *
 * <h2>What it deliberately does not carry</h2>
 *
 * No villager, no offer index, no path, no anchor. Those are <b>attempt evidence</b>, valid for one
 * attempt, and putting them here would turn a momentary observation into durable identity — after
 * which the plan would keep pointing at a villager that has walked away, or an offer index that now
 * means something else. The plan holds the two things that genuinely persist across steps:
 * {@link #consumerKey()} (who wants this) and {@link #desiredOutput()} (what they want).
 *
 * <h2>Why it is not persisted</h2>
 *
 * Transient by construction. A save/reload simply loses it, and the current external demand rebuilds
 * it if it still exists — which is the correct behaviour, because a chain resurrected from disk would
 * be an intention with no live consumer behind it. This also means <b>Gate RET-1e does not apply</b>:
 * there is no store, so there is nothing to sweep on permanent removal.
 */
public record TradeChainPlan(
        ResourceLocation consumerKey,
        ResourceLocation desiredOutput,
        int desiredQuantity,
        long createdAtTick,
        long expiresAtTick,
        Step step) {

    /** Default lifetime. Long enough to walk a village, short enough not to outlive its reason. */
    public static final long DEFAULT_LIFETIME_TICKS = 6_000L;

    public enum Step {
        /** Selling disposable material to close the emerald deficit for the BUY below. */
        SELL_TO_FUND,
        /** Enough emeralds are held; the purchase itself is what remains. */
        BUY_TARGET
    }

    public TradeChainPlan {
        Objects.requireNonNull(consumerKey, "consumerKey");
        Objects.requireNonNull(desiredOutput, "desiredOutput");
        Objects.requireNonNull(step, "step");
        if (desiredQuantity <= 0) {
            throw new IllegalArgumentException("desiredQuantity must be positive: " + desiredQuantity);
        }
        if (expiresAtTick <= createdAtTick) {
            throw new IllegalArgumentException("a chain must expire after it is created");
        }
    }

    /**
     * Open a chain for a consumer that already exists.
     *
     * <p>There is no no-consumer constructor on purpose: a chain with no owner would be an appetite,
     * and the whole point of this slice is that appetite comes from outside (req 1).
     */
    public static TradeChainPlan forConsumer(
            ResourceLocation consumerKey, ResourceLocation desiredOutput, int desiredQuantity,
            long nowTick) {
        return new TradeChainPlan(consumerKey, desiredOutput, desiredQuantity,
                nowTick, nowTick + DEFAULT_LIFETIME_TICKS, Step.SELL_TO_FUND);
    }

    public boolean expired(long nowTick) {
        return nowTick >= expiresAtTick;
    }

    /** Step changes; identity does not. */
    public TradeChainPlan at(Step next) {
        return next == step
                ? this
                : new TradeChainPlan(consumerKey, desiredOutput, desiredQuantity,
                        createdAtTick, expiresAtTick, next);
    }
}
