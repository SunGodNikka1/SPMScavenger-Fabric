package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * D-VR-077 step 5.5 — <b>two different questions about the same backpack.</b>
 *
 * <h2>The mismatch this resolves</h2>
 *
 * Reserve accounting is legitimately <b>category-level</b>: the craft chain needs "three logs" and
 * does not care which component variant supplies them. Payment is legitimately <b>exact</b>:
 * {@code TradeTransaction.debit} spends stacks matching by
 * {@code ItemStack.isSameItemSameComponents}, and {@link OfferRef.Requote} identifies a quote by item
 * and components precisely because that is what decides its price.
 *
 * <p>Planning counted by item alone for both. That reads as fail-safe — the exact debit refuses to
 * spend the wrong stacks — but it is not harmless:
 *
 * <pre>
 * backpack   63x oak_log  +  1x oak_log{component X}     reserve 3
 * quote      22x oak_log{component X} -> 1 emerald
 *
 * planning   held 64, disposable 61, 22 &lt;= 61            -> fundable
 * truth      exact component-X units held: 1              -> CANNOT_AFFORD
 * </pre>
 *
 * The mob commits to a route it can never execute, walks to the merchant, fails, and may pass over
 * a route that would have worked. Vanilla boards rarely produce component-bearing costs, which is
 * why this stayed invisible; a re-quoting source makes component-exact costs ordinary.
 *
 * <h2>Why not just make the reserve exact</h2>
 *
 * Because it would be wrong. Reserving three specific component-X logs when the chain only needs
 * three logs of any kind would refuse legal sales, and reserving three of <i>each</i> variant would
 * multiply the reserve by the number of variants held. The reserve stays category-level; only
 * feasibility becomes exact, and the two are combined by taking the smaller answer.
 */
public final class TradeInventoryFacts {

    private TradeInventoryFacts() {
    }

    /**
     * Units matching {@code sample} <b>exactly</b> — item and components — which is what
     * {@code TradeTransaction.debit} will actually be able to spend.
     */
    public static int countExact(Container backpack, ItemStack sample) {
        if (backpack == null || sample == null || sample.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack held = backpack.getItem(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, sample)) {
                total += held.getCount();
            }
        }
        return total;
    }

    /**
     * Sell uses that are affordable under <b>both</b> facts.
     *
     * <p>Category disposability answers "may we spend this much of this material at all"; exact
     * availability answers "do we physically hold that many of this variant". A route needs both,
     * and taking the minimum avoids claiming that the category reserve belongs to any particular
     * variant.
     *
     * @param categoryDisposable surplus after the category-level reserve
     * @param exactAvailable units matching the cost stack exactly
     * @param unitsPerUse the offer's cost A count
     */
    public static int affordableExactUses(
            int categoryDisposable, int exactAvailable, int unitsPerUse) {
        if (unitsPerUse <= 0) {
            return 0;
        }
        return Math.min(
                SellExpendabilityPolicy.affordableSellUses(categoryDisposable, unitsPerUse),
                SellExpendabilityPolicy.affordableSellUses(exactAvailable, unitsPerUse));
    }
}
