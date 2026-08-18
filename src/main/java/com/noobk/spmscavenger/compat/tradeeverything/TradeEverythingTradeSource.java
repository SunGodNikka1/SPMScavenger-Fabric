package com.noobk.spmscavenger.compat.tradeeverything;

import com.noobk.spmscavenger.village.trade.OfferRef;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.TradeOpportunityQuery;
import com.noobk.spmscavenger.village.trade.TradeOpportunitySource;
import com.noobk.spmscavenger.village.trade.TradeSourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 6 — Trade Everything as an ordinary market source.
 *
 * <h2>What it does not do</h2>
 *
 * No fake {@code ServerPlayer}. No {@code MerchantMenu} or {@code MerchantContainer}. No
 * {@code setTradingPlayer}. <b>Nothing is inserted into {@code villager.getOffers()}.</b> P0-2 proved
 * at runtime that a detached synthetic offer executes correctly: {@code notifyTrade} never reads the
 * board, upstream's {@code afterTrade} hook fires on the offer argument alone, and the villager's
 * real board is unchanged before, during and after.
 *
 * <p>That makes this source <i>safer</i> than upstream's own path, which must insert at index 0,
 * resync, and strip on save.
 *
 * <h2>Quoting is bounded by the caller's permission</h2>
 *
 * Only {@link TradeOpportunityQuery#authorizedSellInputs()} are quoted — already canonicalized to
 * count 1, de-duplicated by item-and-components, and capped. This source takes no {@code Container}
 * and makes no disposition decision: it answers "if you sold this, the market says X", never "you
 * may sell this".
 *
 * <h2>Revalidation is strict, and deliberately unlike vanilla</h2>
 *
 * Vanilla re-reads the same board object and asks transaction equivalence. Here Q1 and Q2 are
 * independently generated objects with no shared identity, so any semantic difference means the
 * pricing inputs moved and the funding arithmetic computed on Q1 no longer holds. The planned
 * snapshot is <b>evidence</b>; the fresh quote is the <b>execution object</b>, and it is returned by
 * reference — rebuilding it would strip the mixin-injected marker upstream's hook keys on.
 */
public final class TradeEverythingTradeSource implements TradeOpportunitySource {

    private final QuoteBridge bridge;

    public TradeEverythingTradeSource(QuoteBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public TradeSourceKey key() {
        return TradeSourceKey.TRADE_EVERYTHING;
    }

    @Override
    public List<OfferSnapshot> offers(Villager villager, TradeOpportunityQuery query) {
        List<OfferSnapshot> quotes = new ArrayList<>();
        if (!usable(villager) || query == null || query.isEmpty()) {
            return quotes;
        }
        bridge.ensureIndexed(villager.level().getServer());
        // Explicit, not incidental: a failed index disables the bridge, and quoting against an index
        // that could not be built is exactly the wrong-economy mistake P0-1 exists to prevent.
        if (!bridge.available()) {
            return quotes;
        }
        for (ItemStack input : query.authorizedSellInputs()) {
            // The board is passed because upstream prices against it - which commodity it buys
            // decides the payout. It is read, never written.
            Optional<MerchantOffer> quoted = bridge.quote(villager, input, villager.getOffers());
            if (quoted.isEmpty()) {
                continue;
            }
            // Requote, not BoardIndex: this offer has no address anywhere. Its identity is the exact
            // input that produced it, count canonicalized away.
            MerchantOffer offer = quoted.get();
            quotes.add(new OfferSnapshot(
                    OfferRef.requote(input),
                    // Round ordinal is the goal's to assign; a source has no view of the round.
                    0,
                    offer.getCostA(), offer.getCostB(), offer.assemble(),
                    offer.getUses(), offer.getMaxUses(), offer.getXp(),
                    offer.getPriceMultiplier(), offer.getDemand(),
                    offer.getSpecialPriceDiff(), offer.shouldRewardExp()));
        }
        return quotes;
    }

    @Override
    public Optional<MerchantOffer> revalidate(Villager villager, OfferSnapshot planned) {
        if (!usable(villager) || planned == null) {
            return Optional.empty();
        }
        // A BoardIndex belongs to the vanilla board and is not ours to resolve. Refusing is correct:
        // guessing would be the inference D-VR-077 rejects.
        if (!(planned.ref() instanceof OfferRef.Requote requote)) {
            return Optional.empty();
        }
        bridge.ensureIndexed(villager.level().getServer());
        Optional<MerchantOffer> fresh =
                bridge.quote(villager, requote.inputKey(), villager.getOffers());
        if (fresh.isEmpty()) {
            return Optional.empty();
        }
        MerchantOffer live = fresh.get();
        if (live.isOutOfStock() || !planned.semanticallyMatches(live)) {
            return Optional.empty();
        }
        // Q2 itself. Never Q1 rebuilt, never a copy.
        return fresh;
    }

    /** Alive and quotable. Sleep and player sessions are physical legality, owned by the executor. */
    private boolean usable(Villager villager) {
        return bridge != null && bridge.available()
                && villager != null && villager.isAlive()
                && villager.level() != null && villager.level().getServer() != null;
    }
}
