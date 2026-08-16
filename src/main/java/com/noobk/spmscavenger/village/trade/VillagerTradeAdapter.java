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
        if (backpack == null || villager == null || !villager.isAlive() || offer == null) {
            return TradeResult.NO_VILLAGER;
        }

        MerchantOffer live = liveOfferAt(villager, offer.index());
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
        if (!TradeTransaction.debit(staged, live.getCostB())) {
            return TradeResult.CANNOT_AFFORD;
        }
        // Preflight: if the output cannot fit, the mob must keep its payment. Doing this on the
        // staged copy is the whole reason the debit above is safe to have run already.
        if (!TradeTransaction.insert(staged, live.assemble())) {
            return TradeResult.NO_ROOM;
        }

        TradeTransaction.commit(backpack, staged);
        // Exactly once, and only after the mob actually holds the goods.
        villager.notifyTrade(live);
        return TradeResult.TRADED;
    }

    private static MerchantOffer liveOfferAt(Villager villager, int index) {
        MerchantOffers offers = villager.getOffers();
        if (index < 0 || index >= offers.size()) {
            return null;
        }
        return offers.get(index);
    }
}
