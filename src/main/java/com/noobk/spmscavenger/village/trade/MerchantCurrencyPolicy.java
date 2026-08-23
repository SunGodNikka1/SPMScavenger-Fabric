package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Shared merchant-currency truth for planning and the staged transaction boundary.
 *
 * <p>The semantic unit remains one emerald. A policy may recognize additional physical
 * denominations, but it must not rewrite a quoted result. Denomination normalization happens only
 * on the staged backpack immediately before exact payment is debited.
 */
public interface MerchantCurrencyPolicy {

    /** Merchant liquidity currently held, expressed in emerald units. */
    int liquidity(Container backpack);

    /** Emerald units contributed by this physical offer result, or zero when it is not currency. */
    int fundingUnits(ItemStack result);

    default boolean recognizesFundingOutput(ItemStack result) {
        return fundingUnits(result) > 0;
    }

    /** Whether this physical cost is part of the emerald-denominated payment. */
    boolean isPaymentCost(ItemStack cost);

    /** Emerald units charged by this one physical cost slot. */
    int paymentUnits(ItemStack cost);

    /**
     * Prepare both payment slots together on the staged inventory.
     *
     * <p>A false result may mutate {@code staged}; callers must discard the whole staged array.
     * The real container is never reachable from this method.
     */
    boolean normalizeForPayment(ItemStack[] staged, ItemStack costA, ItemStack costB);
}
