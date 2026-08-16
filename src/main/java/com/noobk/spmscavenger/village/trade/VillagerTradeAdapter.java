package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * V2-A — execute a villager trade server-side, with no menu and no fake player (D-VR-005).
 *
 * <h2>What makes this possible</h2>
 *
 * {@code AbstractVillager#notifyTrade(MerchantOffer)} takes <b>no {@code Player} parameter</b>: it
 * increments uses, awards villager XP and plays the sound. The player-typed parts of the merchant
 * contract — {@code setTradingPlayer}, {@code updateSpecialPrices(Player)}, the {@code TRADE}
 * criterion — are exactly the parts a PlayerMob does not need in gen-1. So the trade runs through
 * vanilla's own bookkeeping rather than around it, and the hero discount stays a V6 concern
 * (B-VR-34).
 *
 * <h2>The two things that will bite an implementer</h2>
 *
 * <ol>
 *   <li><b>{@code getResult()} is the live field.</b> {@code getCostA()} copies, so the accessors are
 *       asymmetric and checking one proves nothing about the other. Output comes from
 *       {@code assemble()}; see {@link OfferSnapshot}.</li>
 *   <li><b>{@code take(a, b)} is menu-shaped.</b> It shrinks only the two stacks handed to it, which
 *       an 8-slot backpack cannot supply for a bulk cost; see {@link TradeTransaction}.</li>
 * </ol>
 *
 * <h2>Ordering is the safety property</h2>
 *
 * Every check runs against a staged copy, and the real backpack is written once, after all of them
 * pass. {@code notifyTrade} follows the commit, so a villager can never record a trade the mob was
 * not paid for. A snapshot is evidence, not authority: the live offer is re-resolved and compared
 * exactly before anything is spent, because the villager may have traded, restocked or levelled
 * since the snapshot was taken.
 */
public final class VillagerTradeAdapter {

    /** Why a trade did not happen. Every value except {@link #TRADED} means nothing was mutated. */
    public enum TradeResult {
        TRADED,
        NO_VILLAGER,
        /**
         * A human player currently holds this merchant's session.
         *
         * <p>V2-E, mandatory: the adapter never calls {@code setTradingPlayer}, so without this check
         * a PlayerMob would transact <b>underneath</b> a live player session. The candidate picker
         * also skips occupied merchants, but a human can begin trading during the mob's walk —
         * <i>planning permission does not authorize execution</i>, so it is re-checked here at the
         * transaction boundary.
         */
        MERCHANT_BUSY,
        /**
         * Asleep, or otherwise not currently able to trade.
         *
         * <p>Distinct from a failure: the candidate is <b>temporarily illegal</b>, so the executor
         * demotes and reselects rather than concluding the trade route is impossible. Nothing lower
         * enforces this on the no-menu path — {@code performTrade} guarded only {@code isAlive()}.
         */
        MERCHANT_UNAVAILABLE,
        OFFER_GONE,
        OFFER_CHANGED,
        OUT_OF_STOCK,
        CANNOT_AFFORD,
        NO_ROOM;

        public boolean succeeded() {
            return this == TRADED;
        }
    }

    private VillagerTradeAdapter() {
    }

    /**
     * Freeze the villager's current offers.
     *
     * <p>Read-only: no {@code setTradingPlayer}, no price update, nothing that would make the act of
     * looking change what is offered.
     */
    public static List<OfferSnapshot> inspectOffers(Villager villager) {
        List<OfferSnapshot> snapshots = new ArrayList<>();
        if (villager == null || !villager.isAlive()) {
            return snapshots;
        }
        MerchantOffers offers = villager.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (offer != null) {
                snapshots.add(OfferSnapshot.of(i, offer));
            }
        }
        return snapshots;
    }

    /**
     * V2-E-R8 — is <b>this exact recorded offer</b> still on this villager's board, unchanged?
     *
     * <h2>Why liveness was not enough</h2>
     *
     * R7 justified a funding SELL with {@code available(buyer)}, which proves only that the buyer
     * entity is currently usable. It does not prove the purchase still exists:
     *
     * <pre>
     * selection   toolsmith A: 5 emerald + 1 diamond -&gt; iron    fletcher B: sticks -&gt; emeralds
     * walk to B   a player trades with A, exhausting the offer / demand repricing moves the cost
     * at B        A is alive, awake, unoccupied -&gt; available(A) == true
     *             the sale proceeds, funding a purchase that no longer exists
     * </pre>
     *
     * Planning permission does not authorize execution, and the purchase is a fact the sale rests on.
     *
     * <h2>This is not the passive sweep the round forbids</h2>
     *
     * The forbidden thing is touching offer lists for villagers that were <b>not already selected</b>,
     * because {@code getOffers()} lazily populates trades across a whole village. This villager was
     * selected — it is carried as attempt evidence precisely because the executor chose it. Read-only:
     * no {@code notifyTrade}, no menu, no FakePlayer, no mutation.
     *
     * @param recorded the snapshot taken at selection, carrying the <b>villager-local</b> index
     * @return the live snapshot when it still matches exactly, otherwise empty
     */
    public static java.util.Optional<OfferSnapshot> revalidateOffer(
            Villager villager, OfferSnapshot recorded) {
        if (!available(villager) || recorded == null) {
            return java.util.Optional.empty();
        }
        MerchantOffers offers = villager.getOffers();
        int index = recorded.index();
        if (index < 0 || index >= offers.size()) {
            return java.util.Optional.empty();
        }
        MerchantOffer live = offers.get(index);
        if (live == null || live.isOutOfStock() || !recorded.matchesLive(live)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(OfferSnapshot.of(index, live));
    }

    /**
     * Whether a villager may be approached for a trade at all.
     *
     * <p>Selection-time legality. Re-checked at the transaction boundary by
     * {@link #performTrade}, because both facts can change during the walk.
     */
    public static boolean available(Villager villager) {
        return villager != null
                && villager.isAlive()
                && !villager.isSleeping()
                && villager.getTradingPlayer() == null;
    }

    /**
     * R8 — are this quote's <b>non-emerald</b> costs still in the backpack?
     *
     * <p>Asked before a funding SELL, about the purchase being funded. Emerald components are
     * deliberately excluded: those are exactly what the chain is selling to obtain, so requiring them
     * here would refuse every funding sale ever made.
     */
    public static boolean canAffordNonEmerald(Container backpack, OfferSnapshot offer) {
        if (backpack == null || offer == null) {
            return false;
        }
        for (ItemStack cost : new ItemStack[] {offer.costA(), offer.costB()}) {
            if (cost.isEmpty() || cost.is(net.minecraft.world.item.Items.EMERALD)) {
                continue;
            }
            if (com.noobk.spmscavenger.ScavengerCrafting.count(backpack, cost.getItem())
                    < cost.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** Whether the backpack currently holds both costs. Does not mutate anything. */
    public static boolean canAfford(Container backpack, OfferSnapshot offer) {
        if (backpack == null || offer == null || !offer.isTradeable()) {
            return false;
        }
        ItemStack[] staged = TradeTransaction.stage(backpack);
        return TradeTransaction.debit(staged, offer.costA())
                && TradeTransaction.debit(staged, offer.costB());
    }

    /**
     * Execute one trade, or change nothing at all.
     *
     * @param offer a snapshot previously taken from this villager; re-validated here against the
     *     live offer before anything is spent
     */
    public static TradeResult performTrade(Container backpack, Villager villager, OfferSnapshot offer) {
        if (backpack == null || villager == null || !villager.isAlive()) {
            return TradeResult.NO_VILLAGER;
        }
        // Race-proof, and deliberately at the transaction boundary rather than only at selection:
        // a human may have opened this merchant while the mob was walking.
        if (villager.getTradingPlayer() != null) {
            return TradeResult.MERCHANT_BUSY;
        }
        if (villager.isSleeping()) {
            return TradeResult.MERCHANT_UNAVAILABLE;
        }
        return executeAgainst(backpack, villager.getOffers(), offer, villager::notifyTrade);
    }

    /**
     * The whole transaction, minus the entity.
     *
     * <p>Split out so the complete chain — revalidation, joint payment, preflight, commit ordering
     * and the single {@code notifyTrade} — is provable without a live {@code Villager} and therefore
     * without a server. {@code performTrade} is the thin wrapper that supplies the villager's own
     * offer list and its {@code notifyTrade}; there is no second implementation to drift.
     *
     * @param notify invoked exactly once, after the commit, with the <b>live</b> offer
     */
    static TradeResult executeAgainst(
            Container backpack,
            MerchantOffers offers,
            OfferSnapshot offer,
            java.util.function.Consumer<MerchantOffer> notify) {
        if (backpack == null || offers == null || offer == null) {
            return TradeResult.NO_VILLAGER;
        }

        MerchantOffer live = liveOfferAt(offers, offer.index());
        if (live == null) {
            return TradeResult.OFFER_GONE;
        }
        if (!offer.matchesLive(live)) {
            return TradeResult.OFFER_CHANGED;
        }
        if (live.isOutOfStock()) {
            return TradeResult.OUT_OF_STOCK;
        }

        ItemStack[] staged = TradeTransaction.stage(backpack);
        if (!TradeTransaction.debit(staged, live.getCostA())) {
            return TradeResult.CANNOT_AFFORD;
        }
        // Both costs are debited from the same staging array, so cost B spends what cost A left.
        // An offer paid in one item for both costs must not be affordable twice over.
        if (!TradeTransaction.debit(staged, live.getCostB())) {
            return TradeResult.CANNOT_AFFORD;
        }
        // Preflight: if the output cannot fit, the mob must keep its payment. Doing this on the
        // staged copy is the whole reason the debits above are safe to have run already.
        if (!TradeTransaction.insert(staged, live.assemble())) {
            return TradeResult.NO_ROOM;
        }

        TradeTransaction.commit(backpack, staged);
        // Exactly once, and only after the mob actually holds the goods.
        notify.accept(live);
        return TradeResult.TRADED;
    }

    private static MerchantOffer liveOfferAt(MerchantOffers offers, int index) {
        if (index < 0 || index >= offers.size()) {
            return null;
        }
        return offers.get(index);
    }
}
