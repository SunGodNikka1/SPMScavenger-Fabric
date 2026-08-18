package com.noobk.spmscavenger.village.trade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * R7 — what a funding SELL is funding, carried from selection to the execution boundary.
 *
 * <h2>Why the purchase travels with the sale</h2>
 *
 * A funding SELL exists only to pay for a specific purchase. Re-deriving that purchase while
 * standing at the seller finds nothing, because the buyer is a different villager; without this the
 * mob would sell to fund a purchase it could no longer prove existed.
 *
 * <h2>D-VR-077 step 3 — the BUY carries its own source</h2>
 *
 * {@link #buySource} is separate from the seller's source on purpose. The sale and the purchase are
 * independently sourced, and the first real case is already known:
 *
 * <pre>
 * Trade Everything synthetic SELL  ->  emeralds  ->  vanilla Toolsmith BUY
 * </pre>
 *
 * Today both are {@link TradeSourceKey#VANILLA}, and the field exists anyway because the failure it
 * prevents is silent: provenance added at selection and dropped the moment the candidate becomes
 * attempt state would leave step 4 inferring the buyer's source at the boundary — the exact
 * inference D-VR-077 forbids.
 *
 * <p>{@code emeraldsRequired} is the purchase price at selection. The <i>deficit</i> is deliberately
 * not stored: it legitimately shrinks during the walk and is recomputed against live inventory.
 */
public record TradeAttemptFunding(
        ResourceLocation consumerKey,
        Villager buyer,
        TradeSourceKey buySource,
        OfferSnapshot buyQuote,
        int emeraldsRequired) {

    /**
     * Fail closed on missing provenance.
     *
     * <p>Once step 5 dispatches revalidation on {@link #buySource}, a null would have exactly one
     * plausible-looking repair — {@code source == null ? inferFrom(ref) : source} — which is the
     * inference D-VR-077 rejects. Rejecting it at construction means that repair can never be
     * needed. Production never builds one today; the point is that it cannot start to.
     */
    public TradeAttemptFunding {
        java.util.Objects.requireNonNull(buySource,
                "a carried purchase must name the source that will revalidate it - provenance is "
                        + "carried, never inferred");
    }
}
