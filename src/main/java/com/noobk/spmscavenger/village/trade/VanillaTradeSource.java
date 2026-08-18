package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 5 — the villager's own {@code MerchantOffers}, resolved by board address.
 *
 * <h2>Market truth only</h2>
 *
 * This answers "does board index 2 still represent the transaction I planned?". It deliberately does
 * <b>not</b> answer "is this villager alive, asleep, or already serving a human" — those are physical
 * legality, they belong to the executor, and they are identical for every source. Mixing them in
 * here would teach a market source about player sessions and sleep policy, and step 6 would then
 * have to teach a second source the same thing.
 *
 * <p>{@code revalidateOffer} used to combine both. That was fine while the adapter was the only
 * resolver; it is not fine once resolution is per-source.
 *
 * <h2>Ownership moved here from the adapter</h2>
 *
 * {@link OfferRef} is a <b>source-local</b> resolution key, so interpreting one is the source's job.
 * After this step no production path asks {@code VillagerTradeAdapter} to read a ref: it receives a
 * resolved {@link MerchantOffer} and owns the transaction, nothing more.
 *
 * <h2>The query is ignored, and that is correct</h2>
 *
 * A board-reading source reports what the villager offers; what the mob may pay with does not change
 * what is on sale. {@link TradeOpportunityQuery} exists for sources that must be <i>asked</i> to
 * quote something, and honouring it here would silently narrow the vanilla candidate set — a
 * behaviour change wearing an architecture change's clothes.
 */
public final class VanillaTradeSource implements TradeOpportunitySource {

    public static final VanillaTradeSource INSTANCE = new VanillaTradeSource();

    private VanillaTradeSource() {
    }

    @Override
    public TradeSourceKey key() {
        return TradeSourceKey.VANILLA;
    }

    @Override
    public List<OfferSnapshot> offers(Villager villager, TradeOpportunityQuery query) {
        List<OfferSnapshot> snapshots = new ArrayList<>();
        // Defensive, not policy: reading a discarded entity's board is meaningless. Sleeping and
        // busy are deliberately NOT checked - a sleeping villager's offers are still real, and
        // refusing them here would silently shrink the candidate set the round ranks over.
        if (villager == null || !villager.isAlive()) {
            return snapshots;
        }
        return snapshot(villager.getOffers());
    }

    /**
     * The board, minus the entity — so board semantics are provable without a world.
     *
     * <p>Same split, and the same reason, as {@code executeAgainst} being carved out of
     * {@code performTrade}: everything interesting is about the offer list, and requiring a live
     * {@code Villager} to test it would mean not testing it.
     */
    static List<OfferSnapshot> snapshot(MerchantOffers offers) {
        List<OfferSnapshot> snapshots = new ArrayList<>();
        if (offers == null) {
            return snapshots;
        }
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            if (offer != null) {
                snapshots.add(OfferSnapshot.of(index, offer));
            }
        }
        return snapshots;
    }

    /**
     * Board resolution plus transaction equivalence.
     *
     * <p>{@code matchesLive} — not strict semantic correspondence. This source hands back the
     * <b>same object</b> the board holds, so the only question is whether the trade about to be
     * performed is the trade that was authorized. {@code demand} and {@code specialPriceDiff}
     * legitimately move during the walk and the live object is what executes; demanding full
     * semantic equality would abort trades that succeed today. Strictness belongs to a source that
     * generates an independent object and has no shared identity to fall back on.
     *
     * @return the live board object, never a copy or a reconstruction
     */
    @Override
    public Optional<MerchantOffer> revalidate(Villager villager, OfferSnapshot planned) {
        if (villager == null || !villager.isAlive() || planned == null) {
            return Optional.empty();
        }
        return resolveOnBoard(villager.getOffers(), planned);
    }

    /** Board resolution, minus the entity. */
    static Optional<MerchantOffer> resolveOnBoard(MerchantOffers offers, OfferSnapshot planned) {
        if (offers == null || planned == null) {
            return Optional.empty();
        }
        // A Requote has no address on this board. Refusing it is correct rather than defensive: it
        // belongs to whichever source generated it, and guessing would be the inference D-VR-077
        // rejects.
        if (!(planned.ref() instanceof OfferRef.BoardIndex board)) {
            return Optional.empty();
        }
        if (board.index() >= offers.size()) {
            return Optional.empty();
        }
        MerchantOffer live = offers.get(board.index());
        if (live == null || live.isOutOfStock() || !planned.matchesLive(live)) {
            return Optional.empty();
        }
        return Optional.of(live);
    }
}
