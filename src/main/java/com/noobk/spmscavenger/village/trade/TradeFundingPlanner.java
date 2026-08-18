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

        /**
         * V2-H0-R1 — can this target actually be completed, or merely quoted?
         *
         * <p>A non-null target with an emerald deficit and no legal SELL leg is a purchase the mob
         * can never finish. Treating its mere existence as "the direct route wins" meant a reachable
         * finished-tool purchase was suppressed by an unfundable ingredient quote, and the mob
         * refused to trade at all. Precedence belongs to the route that can act, not the one that
         * appears first.
         */
        public boolean actionable() {
            if (funded()) {
                return true;
            }
            // R2: `usable` only says the leg can perform one sale. A leg that yields 2 emeralds
            // against a 10-emerald deficit is usable and still cannot complete the purchase, and
            // treating it as actionable suppressed a fully fundable projected route - the previous
            // round's defect narrowed from "no SELL leg" to "partial SELL leg".
            return deficit != null
                    && sellLeg != null
                    && sellLeg.fullyFunds(deficit.emeraldsNeeded());
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
            // Same comparator the registrar applies within TRADE: utility desc, then round ordinal.
            if (evaluation.utility() > bestUtility
                    || (evaluation.utility() == bestUtility && offer.rankOrdinal() < bestIndex)) {
                bestUtility = evaluation.utility();
                bestIndex = offer.rankOrdinal();
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
                authorizeFunding(deficit, offers, best, backpack, mainHand, offHand, reservedUnits));
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
     * The one exact SELL quote permitted to fund this deficit — chosen by <b>policy</b>, not by list
     * order.
     *
     * <h2>R7: list order was a deadlock, not a preference</h2>
     *
     * R6 returned the first authorized offer it found. That is not merely suboptimal — it refuses
     * trades that are plainly available:
     *
     * <pre>
     * deficit 2 emeralds, 40 disposable sticks
     * SELL A (first)  30 sticks -&gt; 1 emerald   1 affordable use   cannot fund 2
     * SELL B (second) 10 sticks -&gt; 1 emerald   4 affordable uses  funds it easily
     * </pre>
     *
     * R6 picked A, {@code TradeChainPolicy} reported {@code sellBlocked}, and the candidate path
     * returned nothing. The mob declines to trade while a perfectly good funding route sits one
     * element further down the list.
     *
     * <p>So the division of authority is restored: <b>V2-D chooses the step, V2-B chooses among the
     * offers within it.</b> Legs that can fully close the bounded deficit outrank legs that cannot —
     * a cheaper unit cost is worthless if it cannot finish the job — and ties fall to V2-B's utility
     * and then offer index, the same comparator the registrar uses.
     *
     * <h2>The BUY's own payment is a reserve too</h2>
     *
     * {@code SellReserveModel} protects existing craft-chain claims. The quote being funded is
     * another real claim: a purchase costing {@code 5 emerald + 12 sticks} needs those twelve sticks
     * to still be there when the emeralds arrive. R6 checked that stock <i>before</i> funding began
     * and then let the funding SELL spend it, so the mob could sell its way out of the purchase it
     * was selling for. That requirement is added to the reserve for the duration.
     *
     * @param buyQuote the purchase being funded, whose non-emerald payment must survive; may be null
     * @return {@code null} when no authorized quote exists
     */
    public static SellFundingLeg authorizeFunding(
            EmeraldDeficit deficit,
            List<OfferSnapshot> sellOffers,
            OfferSnapshot buyQuote,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            Function<ItemStack, OptionalInt> reservedUnits) {
        if (deficit == null || sellOffers == null || backpack == null) {
            return null;
        }
        SellFundingLeg best = null;
        boolean bestFunds = false;
        float bestUtility = -Float.MAX_VALUE;
        int bestIndex = Integer.MAX_VALUE;

        for (OfferSnapshot offer : sellOffers) {
            SellFundingLeg leg = legFor(
                    deficit, offer, buyQuote, backpack, mainHand, offHand, reservedUnits);
            if (leg == null) {
                continue;
            }
            TradeEvaluationPolicy.Result evaluated =
                    TradeEvaluationPolicy.evaluateSell(deficit, leg.authorization(), offer);
            if (!evaluated.viable()) {
                continue;
            }
            float utility = evaluated.evaluation().orElseThrow().utility();
            boolean funds = leg.fullyFunds(deficit.emeraldsNeeded());

            // Sufficiency first: an unaffordable bargain funds nothing. Then V2-B, then index, which
            // is the registrar's own ordering.
            if (best == null
                    || (funds && !bestFunds)
                    || (funds == bestFunds && utility > bestUtility)
                    // R8: the documented ordinal tie-break, made explicit. It previously relied on the
                    // caller happening to build the list in ascending index order - true today, and
                    // exactly the kind of accidental invariant that stops being true silently.
                    || (funds == bestFunds && utility == bestUtility && leg.offer().rankOrdinal() < bestIndex)) {
                best = leg;
                bestFunds = funds;
                bestUtility = utility;
                bestIndex = leg.offer().rankOrdinal();
            }
        }
        return best;
    }

    /** One candidate leg, or {@code null} when this offer cannot legally fund anything. */
    private static SellFundingLeg legFor(
            EmeraldDeficit deficit,
            OfferSnapshot offer,
            OfferSnapshot buyQuote,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            Function<ItemStack, OptionalInt> reservedUnits) {
        if (!offer.isTradeable() || offer.outOfStock() || !offer.result().is(Items.EMERALD)) {
            return null;
        }
        // Authorizing from costA while the transaction also debits costB would hand out permission
        // for a material nobody examined.
        if (!offer.costB().isEmpty()) {
            return null;
        }
        ItemStack wanted = offer.costA();
        int held = ScavengerCrafting.count(backpack, wanted.getItem());
        if (held <= 0) {
            return null;
        }
        // An unmodelled material is REFUSED, never treated as reserve-free. Reading an absent model
        // as zero is what made SellExpendabilityPolicy's arithmetic decorative.
        OptionalInt reserved = reservedUnits.apply(wanted);
        if (reserved.isEmpty()) {
            return null;
        }
        int reserve = reserved.getAsInt() + owedToPurchase(buyQuote, wanted);
        int disposable = SellExpendabilityPolicy.disposableUnits(
                wanted, held, reserve, mainHand, offHand);
        if (disposable < wanted.getCount()) {
            return null;
        }
        // Uses, not units - and never more uses than the merchant has left. Planning a two-sale
        // sequence against an offer with one use remaining is a deficit that cannot close.
        int affordable = Math.min(
                SellExpendabilityPolicy.affordableSellUses(disposable, wanted.getCount()),
                Math.max(0, offer.maxUses() - offer.uses()));
        if (affordable <= 0) {
            return null;
        }
        return new SellFundingLeg(
                offer,
                new SellAuthorization(wanted, disposable, deficit.consumerKey()),
                offer.result().getCount(),
                affordable);
    }

    /** Units of {@code material} the funded purchase still has to pay with. */
    public static int owedToPurchase(OfferSnapshot buyQuote, ItemStack material) {
        if (buyQuote == null || material.isEmpty()) {
            return 0;
        }
        int owed = 0;
        for (ItemStack cost : new ItemStack[] {buyQuote.costA(), buyQuote.costB()}) {
            if (!cost.isEmpty() && !cost.is(Items.EMERALD)
                    && ItemStack.isSameItemSameComponents(cost, material)) {
                owed += cost.getCount();
            }
        }
        return owed;
    }
}
