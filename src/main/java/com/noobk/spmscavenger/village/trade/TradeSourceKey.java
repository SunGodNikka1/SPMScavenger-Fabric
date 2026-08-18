package com.noobk.spmscavenger.village.trade;

/**
 * D-VR-077 step 3 — <b>which market source owns an offer's revalidation.</b>
 *
 * <h2>A key, not a behaviour</h2>
 *
 * This deliberately has no methods and no dispatch. Step 3 carries provenance through selection and
 * attempt evidence so that step 4 can hand revalidation to the right source without anyone having to
 * <i>work out</i> which one that is. Adding behaviour now would smuggle the opportunity-source seam
 * into a step whose value is that nothing about vanilla changes.
 *
 * <h2>Why it cannot be derived from {@link OfferRef}</h2>
 *
 * The tempting shortcut is {@code offer.ref() instanceof BoardIndex ? VANILLA : TRADE_EVERYTHING}.
 * D-VR-077 rejected it explicitly: that works for exactly two sources and turns {@code OfferRef} into
 * a disguised source enum, re-creating the overload the step-2 split removed. The two facts answer
 * different questions and must be stored separately:
 *
 * <pre>
 * OfferRef        "how this source re-resolves me"
 * TradeSourceKey  "which source owns me"
 * </pre>
 *
 * Today every production candidate is {@link #VANILLA}, and keeping the facts apart anyway is the
 * whole point — a single-valued enum that is always carried is what makes the second value cheap and
 * safe to add.
 *
 * <h2>Not a policy input</h2>
 *
 * No ranking, utility, or permission decision may read this. Where an offer came from is not a
 * reason to prefer it; the economics already say everything policy is entitled to know.
 */
public enum TradeSourceKey {

    /** The villager's own {@code MerchantOffers}, resolved by board address. */
    VANILLA
}
