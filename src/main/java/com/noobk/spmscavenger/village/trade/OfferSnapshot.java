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
        OfferRef ref,
        /**
         * Deterministic ordering within <b>one planning round</b>, and nothing else.
         *
         * <p><b>Never compare this for identity.</b> The same offer legitimately carries different
         * ordinals depending on how it was reached: {@link #of} defaults it to the board index for
         * a single-villager inspection, while {@code TradeWithVillagerGoal} assigns a flat ordinal
         * across every villager in the round. Execution-boundary correspondence is {@link #ref}
         * plus the cost/result semantics.
         */
        int rankOrdinal,
        ItemStack costA,
        ItemStack costB,
        ItemStack result,
        int uses,
        int maxUses,
        /**
         * D-VR-077 step 4 — the rest of the offer's semantic state, captured so an
         * <b>independently re-quoting</b> source can demand strict correspondence between what it
         * planned and what it re-derives.
         *
         * <p><b>Observational for vanilla.</b> {@link #matchesLive} is unchanged and must stay that
         * way: it asks transaction-equivalence, and {@code demand} / {@code specialPriceDiff}
         * legitimately move between selection and execution while the vanilla path executes the live
         * object anyway. Strengthening it with these fields would abort trades that succeed today.
         */
        int xp,
        float priceMultiplier,
        int demand,
        int specialPriceDiff,
        boolean rewardExp) {

    public OfferSnapshot {
        costA = costA == null ? ItemStack.EMPTY : costA.copy();
        costB = costB == null ? ItemStack.EMPTY : costB.copy();
        result = result == null ? ItemStack.EMPTY : result.copy();
    }

    /**
     * Capture a live offer. {@code assemble()} and {@code getCostA()} only.
     *
     * <p>{@code rankOrdinal} defaults to the board index because within a <b>single</b> villager's
     * board those genuinely are the same ordering, and that is what every direct caller of this
     * factory passes. A multi-villager round must not rely on the default: two villagers both own
     * board index {@code 0}, so {@code TradeWithVillagerGoal} assigns its own flat ordinal through
     * {@link #withRankOrdinal(int)}.
     */
    public static OfferSnapshot of(int index, MerchantOffer offer) {
        return new OfferSnapshot(
                OfferRef.board(index),
                index,
                offer.getCostA(),
                offer.getCostB(),
                offer.assemble(),
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand(),
                offer.getSpecialPriceDiff(),
                offer.shouldRewardExp());
    }

    /**
     * The same offer, ranked at a round-local position.
     *
     * <p>{@code ref} is untouched: where the offer lives on its villager's board has nothing to do
     * with where it sorts among candidates from several villagers. Replaces the goal's old trick of
     * constructing a second snapshot whose {@code index} field held the flat slot instead of the
     * board address.
     */
    public OfferSnapshot withRankOrdinal(int ordinal) {
        return new OfferSnapshot(ref, ordinal, costA, costB, result, uses, maxUses,
                xp, priceMultiplier, demand, specialPriceDiff, rewardExp);
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
