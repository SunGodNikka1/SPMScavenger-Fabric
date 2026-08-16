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
        // R4: ranked by V2-B itself, not by a parallel rule. The R3 version divided emerald cost by
        // the offer's FULL result count, which disagrees with V2-B whenever the deficit is smaller
        // than the stack: for a deficit of 1, V2-B caps contribution at 1 and prefers 9->1 (unit 9)
        // over 12->4 (contribution 1, unit 12). One ranking definition, no drift.
        OfferSnapshot best = null;
        float bestUtility = -Float.MAX_VALUE;
        int bestIndex = Integer.MAX_VALUE;

        for (OfferSnapshot offer : offers) {
            if (!offer.costA().is(Items.EMERALD)) {
                continue;
            }
            TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(demand, offer);
            if (!result.viable()) {
                continue;
            }
            TradeEvaluation evaluation = result.evaluation().orElseThrow();
            // Same comparator the registrar applies within TRADE: utility desc, then offer index.
            if (evaluation.utility() > bestUtility
                    || (evaluation.utility() == bestUtility && offer.index() < bestIndex)) {
                bestUtility = evaluation.utility();
                bestIndex = offer.index();
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
     * @param reservedUnits units an existing consumer or craft chain has already claimed, or empty
     *     when the material is unmodelled — in which case it is refused rather than assumed spare
     */
    public static SellAuthorization authorizeFunding(
            EmeraldDeficit deficit,
            List<OfferSnapshot> sellOffers,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            java.util.function.Function<ItemStack, java.util.OptionalInt> reservedUnits) {
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
            // R4: an unmodelled material is REFUSED, never treated as reserve-free. Reading an
            // absent model as zero is what made SellExpendabilityPolicy's arithmetic decorative.
            java.util.OptionalInt reserved = reservedUnits.apply(wanted);
            if (reserved.isEmpty()) {
                continue;
            }
            int disposable = SellExpendabilityPolicy.disposableUnits(
                    wanted, held, reserved.getAsInt(), mainHand, offHand);
            if (disposable >= wanted.getCount()) {
                return new SellAuthorization(wanted, disposable, deficit.consumerKey());
            }
        }
        return SellAuthorization.none(deficit);
    }
}
