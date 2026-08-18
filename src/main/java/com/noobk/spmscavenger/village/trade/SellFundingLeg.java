package com.noobk.spmscavenger.village.trade;

import java.util.Objects;

/**
 * V2-E-R6 — the <b>one exact SELL quote</b> this iteration's arithmetic is about.
 *
 * <h2>The defect Task 50 predicted before an executor existed</h2>
 *
 * R5 derived {@code emeraldsPerSellUse} by scanning the offer list and taking the <i>first</i>
 * authorized SELL. The registrar then ranked offers independently, and the executor attempted
 * whichever SELL came out on top. Nothing made those the same offer:
 *
 * <pre>
 * V2-D computed requiredSellUses / sellBlocked  against  SELL A  (1 emerald per use)
 * V2-E physically attempted                             SELL B  (3 emeralds per use)
 * </pre>
 *
 * "Right arithmetic against the wrong offer" — the same shape as the deficit derived from a quote the
 * ranking would not have chosen, one layer further in. Two sells would be planned where one sufficed,
 * or {@code sellBlocked} would fire for stock the attempted offer never needed.
 *
 * <h2>Transient by construction</h2>
 *
 * This is <b>attempt evidence</b>, valid for one evaluation. It never enters {@link TradeChainPlan},
 * which deliberately holds only what survives across steps — consumer and desired output. An offer
 * index that outlived its observation would point at a different trade the next time a villager
 * restocked, and a villager reference would keep pointing at someone who has walked away.
 *
 * @param offer the exact quote whose economics the chain is reasoning about and the executor will
 *     attempt; identity, not a lookup key
 * @param authorization permission for this offer's cost, from the real reserve model
 * @param emeraldsPerUse what one successful use of <b>this</b> offer yields
 * @param affordableUses how many uses the authorized stock covers — <b>uses, not item units</b>,
 *     which is the unit {@code TradeChainPolicy} compares against
 */
public record SellFundingLeg(
        OfferSnapshot offer,
        SellAuthorization authorization,
        int emeraldsPerUse,
        int affordableUses) {

    public SellFundingLeg {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(authorization, "authorization");
    }

    /**
     * Can this leg close the whole deficit by itself?
     *
     * <p>The decisive property when choosing between legs: a cheaper unit cost is worth nothing if
     * the affordable use count cannot reach the target. R6 took the first authorized offer in list
     * order, so a 30-stick sale that afforded one use beat a 10-stick sale that afforded four, and
     * the chain reported {@code sellBlocked} while a working route sat one element later.
     */
    public boolean fullyFunds(int emeraldsNeeded) {
        return usable() && affordableUses * emeraldsPerUse >= emeraldsNeeded;
    }

    /** Whether this leg can actually contribute anything right now. */
    public boolean usable() {
        return !authorization.isEmpty() && emeraldsPerUse > 0 && affordableUses > 0;
    }

    /**
     * Does this leg still describe the offer being attempted, and is that offer still authorized?
     *
     * <p>Both halves matter at the execution boundary: the quote must be the one that was planned
     * <b>and</b> the permission must still hold against inventory as it now stands.
     */
    public boolean covers(OfferSnapshot attempted) {
        return attempted != null
                // Round-local candidate identity, NOT a board address. Board indexes are
                // villager-local, so two villagers both owning index 0 used to compare equal here -
                // a sell leg on one merchant could match an attempt on another.
                && attempted.rankOrdinal() == offer.rankOrdinal()
                && net.minecraft.world.item.ItemStack
                        .isSameItemSameComponents(attempted.costA(), offer.costA())
                && attempted.costA().getCount() == offer.costA().getCount()
                && net.minecraft.world.item.ItemStack
                        .isSameItemSameComponents(attempted.result(), offer.result())
                && attempted.result().getCount() == offer.result().getCount()
                && authorization.permits(attempted.costA());
    }
}
