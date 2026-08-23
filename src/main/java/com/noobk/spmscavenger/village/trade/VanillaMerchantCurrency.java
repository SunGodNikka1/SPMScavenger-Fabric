package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla villagers recognize loose emeralds only. */
public enum VanillaMerchantCurrency implements MerchantCurrencyPolicy {
    INSTANCE;

    @Override
    public int liquidity(Container backpack) {
        return backpack == null ? 0 : backpack.countItem(Items.EMERALD);
    }

    @Override
    public int fundingUnits(ItemStack result) {
        return result != null && result.is(Items.EMERALD) ? result.getCount() : 0;
    }

    @Override
    public boolean isPaymentCost(ItemStack cost) {
        return cost != null && cost.is(Items.EMERALD);
    }

    @Override
    public int paymentUnits(ItemStack cost) {
        return isPaymentCost(cost) ? cost.getCount() : 0;
    }

    @Override
    public boolean normalizeForPayment(ItemStack[] staged, ItemStack costA, ItemStack costB) {
        return staged != null;
    }
}
