package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * V2-A — one villager offer, frozen and owned by us.
 *
 * <h2>Every stack here is a copy, deliberately</h2>
 *
 * {@code MerchantOffer}'s accessors are asymmetric: {@link MerchantOffer#getCostA()} returns
 * {@code baseCostA.itemStack().copyWithCount(n)} — a copy — while {@link MerchantOffer#getResult()}
 * returns the <b>live {@code result} field</b>, and {@code getBaseCostA()} returns the live
 * {@code ItemCost} stack. An implementer who checks the cost side, finds it safe and generalises
 * will hand a villager's own offer stack to a container; from then on any count change corrupts that
 * offer permanently, the same instance can reach two mobs' backpacks, and it persists to the world.
 *
 * <p>So this record copies at construction, and the adapter reads output through
 * {@link MerchantOffer#assemble()} only — which is what vanilla's own {@code MerchantResultSlot}
 * does.
 *
 * <p>A snapshot is <b>evidence of what an offer looked like</b>, never authority to execute it. The
 * live offer is re-resolved and re-checked against this snapshot immediately before any commit
 * ({@link #matchesLive}), because the villager may have traded, restocked or levelled in between.
 */
public record OfferSnapshot(
        int index,
        ItemStack costA,
        ItemStack costB,
        ItemStack result,
        int uses,
        int maxUses) {

    public OfferSnapshot {
        costA = costA == null ? ItemStack.EMPTY : costA.copy();
        costB = costB == null ? ItemStack.EMPTY : costB.copy();
        result = result == null ? ItemStack.EMPTY : result.copy();
    }

    /** Capture a live offer. {@code assemble()} and {@code getCostA()} only. */
    public static OfferSnapshot of(int index, MerchantOffer offer) {
        return new OfferSnapshot(
                index,
                offer.getCostA(),
                offer.getCostB(),
                offer.assemble(),
                offer.getUses(),
                offer.getMaxUses());
    }

    public boolean outOfStock() {
        return uses >= maxUses;
    }

    public boolean hasSecondCost() {
        return !costB.isEmpty();
    }

    /** A zero-result or zero-cost offer is not tradeable, whatever else is true of it. */
    public boolean isTradeable() {
        return !result.isEmpty() && !costA.isEmpty() && !outOfStock();
    }

    /**
     * Exact correspondence with the live offer, checked immediately before commit.
     *
     * <p>Compares item <em>and</em> count on both costs and the result. The count half is the
     * interesting one: {@code specialPriceDiff} and demand updates move prices without changing
     * items, so an item-only check would let the mob pay a price it never agreed to.
     */
    public boolean matchesLive(MerchantOffer live) {
        if (live == null) {
            return false;
        }
        return sameStack(costA, live.getCostA())
                && sameStack(costB, live.getCostB())
                && sameStack(result, live.assemble());
    }

    private static boolean sameStack(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() && actual.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(expected, actual)
                && expected.getCount() == actual.getCount();
    }
}
