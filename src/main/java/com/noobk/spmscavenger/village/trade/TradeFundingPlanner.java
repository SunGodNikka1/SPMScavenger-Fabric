package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * V2-E-R3 — turn one external demand into a fundable purchase, and only then into a funding leg.
 *
 * <h2>The deficit belongs to a specific quote</h2>
 *
 * R2 derived the emerald shortfall from <i>the cheapest emerald-costed BUY found anywhere</i>,
 * independently of the BUY the ranking would actually choose. That is Task 50's "right arithmetic
 * against the wrong offer", one layer earlier: the mob would sell exactly enough for a purchase it
 * was not going to make.
 *
 * <p>So the order is fixed:
 *
 * <pre>
 * live external demand
 *      -> choose the BUY quote this iteration is serving
 *      -> derive the deficit FROM THAT QUOTE
 *      -> authorize a disposable material to fund it
 *      -> one transaction
 *      -> re-derive from actual inventory
 * </pre>
 *
 * Nothing here persists. {@code TradeChainPlan} deliberately keeps only consumer and desired output;
 * offers stay momentary evidence, so every iteration is free to pick the best current quote and the
 * arithmetic always describes the quote being served.
 */
public final class TradeFundingPlanner {

    /**
     * @param buyOffer the purchase this iteration serves; never null when {@code fundable}
     * @param deficit emeralds still missing for {@code buyOffer}, or {@code null} when already funded
     */
    public record FundingTarget(OfferSnapshot buyOffer, EmeraldDeficit deficit) {

        public boolean funded() {
            return deficit == null;
        }
    }

    private TradeFundingPlanner() {
    }

    /**
     * Choose the BUY quote to serve, and the shortfall it implies.
     *
     * @return {@code null} when nothing on offer satisfies the demand for emeralds
     */
    public static FundingTarget chooseFundingTarget(
            WorkDemandPolicy.MaterialDemand demand, List<OfferSnapshot> offers, Container backpack) {
        if (demand == null || offers == null || backpack == null) {
            return null;
        }
        OfferSnapshot best = null;
        float bestUnitCost = Float.MAX_VALUE;

        for (OfferSnapshot offer : offers) {
            if (!offer.isTradeable() || !offer.costA().is(Items.EMERALD)) {
                continue;
            }
            if (!demand.materialKey().equals(
                    BuiltInRegistries.ITEM.getKey(offer.result().getItem()))) {
                continue;
            }
            // Same ordering V2-B would apply within TRADE: cheapest per unit acquired. Choosing the
            // quote first is what keeps the deficit and the purchase describing the same trade.
            float unitCost = offer.costA().getCount() / (float) offer.result().getCount();
            if (unitCost < bestUnitCost) {
                bestUnitCost = unitCost;
                best = offer;
            }
        }
        if (best == null) {
            return null;
        }
        int held = ScavengerCrafting.count(backpack, Items.EMERALD);
        int shortfall = best.costA().getCount() - held;
        // No shortfall means no emerald appetite exists at all - the mob simply buys.
        return new FundingTarget(best, shortfall > 0
                ? new EmeraldDeficit(demand.consumerKey(), shortfall)
                : null);
    }

    /**
     * Which disposable material may fund this deficit, given what the merchant will actually buy.
     *
     * <p>Delegates permission to {@link SellExpendabilityPolicy} — the same layer that stops a wooden
     * pickaxe becoming furnace fuel — rather than deciding here what the mob can spare.
     *
     * @param sellOffers offers whose result is emeralds
     * @param reservedUnits units an existing consumer or craft chain has already claimed
     */
    public static SellAuthorization authorizeFunding(
            EmeraldDeficit deficit,
            List<OfferSnapshot> sellOffers,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            java.util.function.ToIntFunction<ItemStack> reservedUnits) {
        if (deficit == null || sellOffers == null || backpack == null) {
            return null;
        }
        for (OfferSnapshot offer : sellOffers) {
            if (!offer.isTradeable() || !offer.result().is(Items.EMERALD)) {
                continue;
            }
            ItemStack wanted = offer.costA();
            int held = ScavengerCrafting.count(backpack, wanted.getItem());
            if (held <= 0) {
                continue;
            }
            int disposable = SellExpendabilityPolicy.disposableUnits(
                    wanted, held, reservedUnits.applyAsInt(wanted), mainHand, offHand);
            if (disposable >= wanted.getCount()) {
                return new SellAuthorization(wanted, disposable, deficit.consumerKey());
            }
        }
        return SellAuthorization.none(deficit);
    }
}
