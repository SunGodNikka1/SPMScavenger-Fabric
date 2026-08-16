package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * V2-B — what one offer would contribute to one demand, and what it would cost.
 *
 * <h2>This is a description, not a verdict</h2>
 *
 * An evaluation says <i>this offer matches, contributes n, and costs this much</i>. It does not say
 * the mob should trade. Comparing trade against gather / smelt / craft, and deciding whether TRADE is
 * a feasible acquisition route at all, belong to V2-C — so this record carries a comparable
 * {@link #utility()} and stops there.
 *
 * <p>{@link #consumerKey()} is copied unchanged from the demand so V2-C can attribute the route to
 * the thing that actually wanted the material. An evaluation that lost its consumer would be an
 * appetite with no owner, which is the shape this slice exists to avoid.
 *
 * @param direction whether the mob receives the demanded material ({@code BUY}) or pays it out for
 *     emeralds ({@code SELL})
 * @param quantityContribution capped at the demand's deficit — never more
 * @param unitPaymentCost payment items per unit of demanded material, the comparable price
 */
public record TradeEvaluation(
        Direction direction,
        ResourceLocation consumerKey,
        ResourceLocation materialKey,
        int offerIndex,
        int quantityContribution,
        ItemStack requiredCostA,
        ItemStack requiredCostB,
        float unitPaymentCost,
        float utility) {

    public enum Direction {
        /** The offer's result is the demanded material. */
        BUY,
        /** The offer's result is emeralds, paid toward a named emerald deficit. */
        SELL
    }

    public TradeEvaluation {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(consumerKey, "consumerKey");
        Objects.requireNonNull(materialKey, "materialKey");
        if (quantityContribution <= 0) {
            throw new IllegalArgumentException("contribution must be positive: " + quantityContribution);
        }
        requiredCostA = requiredCostA == null ? ItemStack.EMPTY : requiredCostA.copy();
        requiredCostB = requiredCostB == null ? ItemStack.EMPTY : requiredCostB.copy();
    }

    public boolean hasSecondCost() {
        return !requiredCostB.isEmpty();
    }

    /** Total payment items for this single trade — what V2-C will have to find. */
    public int totalPaymentItems() {
        return requiredCostA.getCount() + requiredCostB.getCount();
    }
}
