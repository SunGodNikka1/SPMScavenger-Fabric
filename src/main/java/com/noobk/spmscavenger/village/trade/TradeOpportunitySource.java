package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 4 — <b>where trade opportunities come from, and who re-resolves them.</b>
 *
 * <h2>Two questions, two methods</h2>
 *
 * <pre>
 * offers(...)      planning: what could this villager trade, given what policy allows us to quote
 * revalidate(...)  execution: is the planned offer still real, and what exact object do we execute
 * </pre>
 *
 * The split exists because resolution differs by source while the transaction does not. Vanilla
 * resolves by board address and checks transaction-equivalence; a re-quoting source generates an
 * independent object and needs strict semantic correspondence, because there is no shared identity
 * to fall back on. Both then hand a live {@link MerchantOffer} to
 * {@code VillagerTradeAdapter.executeResolved}, which remains the sole transaction owner: one
 * staging array, one debit pair, one preflight, one commit, one {@code notifyTrade}.
 *
 * <h2>What a source is not allowed to decide</h2>
 *
 * <ul>
 *   <li><b>Spend permission.</b> {@link #offers} takes a {@link TradeOpportunityQuery} of
 *       already-authorized stack kinds, never a {@code Container}. Disposition is V2 policy.</li>
 *   <li><b>Affordability.</b> {@link #revalidate} takes no inventory either. Neither proven source
 *       needs it to establish market truth — vanilla reads the board, a re-quoting source reads the
 *       board and its own quoter — and the adapter owns payment after resolution.</li>
 *   <li><b>Ranking.</b> No policy decision may read {@link #key()}. Where an offer came from is not
 *       a reason to prefer it.</li>
 * </ul>
 *
 * <h2>Provenance is carried, never inferred</h2>
 *
 * {@link #key()} exists so attempt evidence can name its owner. Nothing may reconstruct it from an
 * {@link OfferRef}: {@code ref instanceof BoardIndex ? VANILLA : ...} works for exactly two sources
 * and turns the ref back into a source enum.
 *
 * <p><b>No implementations exist yet.</b> This step establishes the contract and its data types;
 * {@code VanillaTradeSource} and the routing of {@code TradeWithVillagerGoal} through it are step 5,
 * deliberately separate so a vanilla behaviour change cannot hide behind a new interface arriving in
 * the same commit.
 */
public interface TradeOpportunitySource {

    /** Stable provenance for every offer this source produces. */
    TradeSourceKey key();

    /**
     * Opportunities this villager currently presents.
     *
     * @param query the stack kinds policy has already authorized for sale, counts canonicalized
     *     away. A source that ignores it — as a board-reading source legitimately does — simply
     *     reports the villager's own offers.
     */
    List<OfferSnapshot> offers(Villager villager, TradeOpportunityQuery query);

    /**
     * Re-resolve a planned offer at the execution boundary.
     *
     * @return the <b>live object to execute</b>, or empty when the opportunity no longer stands. The
     *     returned offer must be the one the source itself produced, never a reconstruction: Trade
     *     Everything marks synthetic offers with a mixin-injected instance field, so rebuilding one
     *     from its own field values silently strips the marker.
     */
    Optional<MerchantOffer> revalidate(Villager villager, OfferSnapshot planned);
}
