package com.noobk.spmscavenger.compat.tradeeverything;

import com.noobk.spmscavenger.village.trade.MerchantCurrencyPolicy;
import com.noobk.spmscavenger.village.trade.TradeTransaction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Trade Everything's emerald-block denomination, independent of synthetic quotation health.
 *
 * <p>The exact version allowlist is intentional. It is pinned to source-confirmed behavior in
 * {@code MerchantMenuMixin#tradeeverything$breakEmeraldBlocks}; retaining the reflective quote
 * shape in a future release is not evidence that this separate economic contract still exists.
 */
public final class TradeEverythingCurrencyProvider implements MerchantCurrencyPolicy {

    private static final int EMERALDS_PER_BLOCK = 9;
    private static final Set<String> SOURCE_VALIDATED_VERSIONS = Set.of("0.3.0", "0.8.0");

    static boolean supportsVersion(String version) {
        return version != null && SOURCE_VALIDATED_VERSIONS.contains(version);
    }

    @Override
    public int liquidity(Container backpack) {
        if (backpack == null) {
            return 0;
        }
        return backpack.countItem(Items.EMERALD)
                + backpack.countItem(Items.EMERALD_BLOCK) * EMERALDS_PER_BLOCK;
    }

    @Override
    public int fundingUnits(ItemStack result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        if (result.is(Items.EMERALD)) {
            return result.getCount();
        }
        if (result.is(Items.EMERALD_BLOCK)) {
            return result.getCount() * EMERALDS_PER_BLOCK;
        }
        return 0;
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
        if (staged == null) {
            return false;
        }
        int needed = paymentUnits(costA) + paymentUnits(costB);
        int loose = TradeTransaction.countItem(staged, Items.EMERALD);
        if (needed <= loose) {
            return true;
        }
        int blocksNeeded = (needed - loose + EMERALDS_PER_BLOCK - 1) / EMERALDS_PER_BLOCK;
        if (!TradeTransaction.debitItem(staged, Items.EMERALD_BLOCK, blocksNeeded)) {
            return false;
        }
        return TradeTransaction.insert(
                staged, new ItemStack(Items.EMERALD, blocksNeeded * EMERALDS_PER_BLOCK));
    }
}
