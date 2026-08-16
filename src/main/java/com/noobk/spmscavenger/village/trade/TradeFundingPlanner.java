package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * V2-E — turn one external demand into a fundable purchase, and only then into a funding leg.
 *
 * <h2>The deficit belongs to a specific quote</h2>
 *
 * R2 derived the emerald shortfall from <i>the cheapest emerald-costed BUY found anywhere</i>,
 * independently of the BUY the ranking would actually choose. That is Task 50's "right arithmetic
 * against the wrong offer": the mob would sell exactly enough for a purchase it was not going to
 * make. So the order is fixed:
 *
 * <pre>
 * live external demand
 *      -&gt; choose the BUY quote this iteration is serving
 *      -&gt; derive the deficit FROM THAT QUOTE
 *      -&gt; authorize one exact SELL quote to fund it
 *      -&gt; one transaction
 *      -&gt; re-derive from actual inventory
 * </pre>
 *
 * <h2>Fundable means payable in full (R6)</h2>
 *
 * R5 skipped any offer whose {@code costA} was not emeralds and then sized the deficit from
 * {@code costA} alone, while V2-A and V2-B both support a second cost. Two ways that lied:
 *
 * <pre>
 * A: 5 emerald + 1 diamond -&gt; item     mob holds no diamond
 * B: 6 emerald             -&gt; item
 * </pre>
 *
 * If A ranked first, R5 funded 5 emeralds and stopped. A could never execute (no diamond), B could
 * never execute (needs 6), and A's deficit was now zero so nothing would ever sell for the sixth
 * emerald. <b>A quote the mob cannot finish paying for is not a funding target</b>, so non-emerald
 * cost components must already be held, and the emerald requirement sums <i>both</i> slots.
 *
 * <p>Nothing here persists. Offers stay momentary evidence, so every iteration picks the best current
 * quote and the arithmetic always describes the quote being served.
 */
public final class TradeFundingPlanner {

    /**
     * @param buyOffer the purchase this iteration serves; never null
     * @param emeraldsRequired emeralds this quote costs across <b>both</b> slots
     * @param deficit emeralds still missing for {@code buyOffer}, or {@code null} when already funded
     * @param sellLeg the exact SELL quote that would close {@code deficit}, or {@code null} when the
     *     purchase is funded or nothing authorized can fund it
     */
    public record FundingTarget(
            OfferSnapshot buyOffer, int emeraldsRequired, EmeraldDeficit deficit,
            SellFundingLeg sellLeg) {

        public boolean funded() {
            return deficit == null;
        }
    }

    private TradeFundingPlanner() {
    }

    /**
     * Choose the BUY quote to serve, the shortfall it implies, and the SELL leg that would close it.
     *
     * @param reservedUnits the reserve model; empty means <b>unmodelled, therefore refused</b>
     * @return {@code null} when nothing on offer is both useful and payable
     */
    public static FundingTarget chooseFundingTarget(
            WorkDemandPolicy.MaterialDemand demand,
            List<OfferSnapshot> offers,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            Function<ItemStack, OptionalInt> reservedUnits) {
        if (demand == null || offers == null || backpack == null) {
            return null;
        }
        // Ranked by V2-B itself, not by a parallel rule. R3 divided emerald cost by the offer's FULL
        // result count, which disagrees with V2-B whenever the deficit is smaller than the stack.
        OfferSnapshot best = null;
        float bestUtility = -Float.MAX_VALUE;
        int bestIndex = Integer.MAX_VALUE;

        for (OfferSnapshot offer : offers) {
            if (!fundable(offer, backpack)) {
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

        int required = emeraldCost(best);
        int shortfall = required - ScavengerCrafting.count(backpack, Items.EMERALD);
        if (shortfall <= 0) {
            // No emerald appetite exists at all - the mob simply buys.
            return new FundingTarget(best, required, null, null);
        }
        EmeraldDeficit deficit = new EmeraldDeficit(demand.consumerKey(), shortfall);
        return new FundingTarget(best, required, deficit,
                authorizeFunding(deficit, offers, backpack, mainHand, offHand, reservedUnits));
    }

    /**
     * Whether the mob could finish paying for this quote if it had the emeralds.
     *
     * <p>Emerald components are what the chain exists to acquire; everything else must already be in
     * hand, or funding it would be paying towards a purchase that cannot complete.
     */
    private static boolean fundable(OfferSnapshot offer, Container backpack) {
        if (!offer.isTradeable() || offer.outOfStock()) {
            return false;
        }
        if (emeraldCost(offer) <= 0) {
            return false;
        }
        return payableIfNotEmerald(offer.costA(), backpack)
                && payableIfNotEmerald(offer.costB(), backpack);
    }

    private static boolean payableIfNotEmerald(ItemStack cost, Container backpack) {
        return cost.isEmpty()
                || cost.is(Items.EMERALD)
                || ScavengerCrafting.count(backpack, cost.getItem()) >= cost.getCount();
    }

    /** Emeralds this quote costs, across <b>both</b> slots. */
    private static int emeraldCost(OfferSnapshot offer) {
        int total = 0;
        if (offer.costA().is(Items.EMERALD)) {
            total += offer.costA().getCount();
        }
        if (offer.costB().is(Items.EMERALD)) {
            total += offer.costB().getCount();
        }
        return total;
    }

    /**
     * The one exact SELL quote permitted to fund this deficit.
     *
     * <p>Permission is delegated to {@link SellExpendabilityPolicy} — the same layer that stops a
     * wooden pickaxe becoming furnace fuel — and the chosen quote is carried as identity so the
     * chain's arithmetic and the executor's attempt cannot describe different offers.
     *
     * @return {@code null} when no authorized quote exists
     */
    public static SellFundingLeg authorizeFunding(
            EmeraldDeficit deficit,
            List<OfferSnapshot> sellOffers,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            Function<ItemStack, OptionalInt> reservedUnits) {
        if (deficit == null || sellOffers == null || backpack == null) {
            return null;
        }
        for (OfferSnapshot offer : sellOffers) {
            if (!offer.isTradeable() || offer.outOfStock() || !offer.result().is(Items.EMERALD)) {
                continue;
            }
            // Authorizing from costA while the transaction also debits costB would hand out
            // permission for a material nobody examined.
            if (!offer.costB().isEmpty()) {
                continue;
            }
            ItemStack wanted = offer.costA();
            int held = ScavengerCrafting.count(backpack, wanted.getItem());
            if (held <= 0) {
                continue;
            }
            // An unmodelled material is REFUSED, never treated as reserve-free. Reading an absent
            // model as zero is what made SellExpendabilityPolicy's arithmetic decorative.
            OptionalInt reserved = reservedUnits.apply(wanted);
            if (reserved.isEmpty()) {
                continue;
            }
            int disposable = SellExpendabilityPolicy.disposableUnits(
                    wanted, held, reserved.getAsInt(), mainHand, offHand);
            if (disposable < wanted.getCount()) {
                continue;
            }
            return new SellFundingLeg(
                    offer,
                    new SellAuthorization(wanted, disposable, deficit.consumerKey()),
                    offer.result().getCount(),
                    // Uses, not units: the unit TradeChainPolicy compares against.
                    SellExpendabilityPolicy.affordableSellUses(disposable, wanted.getCount()));
        }
        return null;
    }
}
