package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * V2-E-R3 — <b>which</b> backpack material may fund a purchase, and how much of it.
 *
 * <h2>The semantic gap this fills</h2>
 *
 * {@code TradeEvaluationPolicy.sell()} required the offer's cost to match {@code demand.materialKey}.
 * R2 handed it the <b>external purchase demand</b>, so funding an iron purchase meant:
 *
 * <pre>
 * demand.material = IRON_INGOT
 * SELL.costA      = WHEAT        -> WRONG_MATERIAL
 * </pre>
 *
 * The only SELL that could fund an iron purchase was <i>iron → emeralds</i>: selling the very
 * material the consumer wants in order to buy it. The bridge between "what the consumer needs" and
 * "what the mob may spend" simply did not exist.
 *
 * <h2>Why not a synthetic MaterialDemand</h2>
 *
 * Manufacturing {@code MaterialDemand(WHEAT)} to satisfy the evaluator would lie about demand
 * semantics: nobody wants wheat. Wheat is expendable funding stock, and an appetite invented to make
 * a signature fit is exactly the emerald-appetite failure one level down.
 *
 * <p>So the three concepts stay separate and each keeps its own meaning:
 *
 * <pre>
 * MaterialDemand     what the external consumer needs
 * EmeraldDeficit     how much funding that consumer's BUY currently lacks
 * SellAuthorization  which exact material may fund it, and how much is spendable
 * </pre>
 *
 * <h2>Permission, never appetite</h2>
 *
 * Produced from {@link SellExpendabilityPolicy}, which finally has a production consumer. It says the
 * mob <i>may</i> spend this material; how much it actually sells is bounded by the deficit through
 * {@code TradeChainPolicy}. <b>Preference and market value cannot make an item spendable</b> — the
 * same invariant as burnable-is-not-expendable, carried into the economy.
 *
 * @param material the exact stack the mob is permitted to sell
 * @param disposableUnits how many units are spare after reserves; never a target
 * @param consumerKey the external consumer this funding serves, carried so the SELL leg is
 *     attributed to the purchase it exists for rather than to the material
 */
public record SellAuthorization(
        ItemStack material,
        int disposableUnits,
        ResourceLocation consumerKey) {

    public SellAuthorization {
        Objects.requireNonNull(consumerKey, "consumerKey");
        material = material == null ? ItemStack.EMPTY : material.copy();
    }

    public boolean isEmpty() {
        return material.isEmpty() || disposableUnits <= 0;
    }

    /** Whether this authorization actually covers the cost the offer is asking for. */
    public boolean permits(ItemStack cost) {
        return !isEmpty()
                && cost != null
                && !cost.isEmpty()
                && ItemStack.isSameItemSameComponents(material, cost)
                && disposableUnits >= cost.getCount();
    }

    /**
     * The authorization for a deficit the mob cannot fund from this material at all.
     *
     * <p>Distinct from "no authorization": it says the material was considered and refused, which is
     * what lets the executor demote a SELL candidate rather than concluding trade is impossible.
     */
    public static SellAuthorization none(EmeraldDeficit deficit) {
        return new SellAuthorization(ItemStack.EMPTY, 0, deficit.consumerKey());
    }
}
