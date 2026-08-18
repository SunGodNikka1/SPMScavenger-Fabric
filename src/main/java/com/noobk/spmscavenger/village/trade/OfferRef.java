package com.noobk.spmscavenger.village.trade;

/**
 * D-VR-077 — <b>how a source re-resolves one of its own offers</b>, and nothing else.
 *
 * <h2>The overload this removes</h2>
 *
 * {@code OfferSnapshot.index} used to carry two unrelated meanings at once:
 *
 * <ol>
 *   <li>a <b>board address</b> — {@code villager.getOffers().get(index)} at execution;</li>
 *   <li>a <b>round-local ranking key</b> — the flat slot {@code TradeWithVillagerGoal} assigns
 *       across villagers so policy can order candidates deterministically.</li>
 * </ol>
 *
 * They are not the same coordinate and they do not even share a namespace: two villagers both
 * legitimately own board index {@code 0}, so a board address cannot identify a candidate within a
 * planning round. That collision was invisible only because the goal quietly built a second
 * {@code OfferSnapshot} carrying the flat slot in the same field, while keeping the real one
 * elsewhere.
 *
 * <p>This type is meaning (1). Meaning (2) is {@code OfferSnapshot.rankOrdinal}. Policy never reads
 * this type; {@code VillagerTradeAdapter} is the only code allowed to interpret a
 * {@link BoardIndex} as an address.
 *
 * <h2>Why sealed</h2>
 *
 * A future Trade Everything source resolves by <b>re-quoting</b>, not by address — there is no
 * index to hold. Sealing makes "this ref is not addressable" a compile-time fact when that arrives,
 * instead of a sentinel value like {@code 9_999} that type-checks fine and then indexes into
 * somebody's offer list. For this step exactly one variant exists, so a non-{@code BoardIndex} ref
 * is structurally impossible rather than merely unexpected.
 *
 * <p>Deliberately <b>no</b> {@code tieBreak()} method. Deriving the round ordinal from the ref is
 * how the two meanings got merged in the first place.
 */
public sealed interface OfferRef permits OfferRef.BoardIndex {

    /**
     * A position in one villager's {@code MerchantOffers}. Villager-local, never global.
     *
     * <p>Negative values are rejected at construction rather than at use: a negative board index has
     * no meaning, and the only way one could arise is a sentinel someone invented to stand for
     * "not on a board" — precisely the pattern this type exists to make impossible.
     */
    record BoardIndex(int index) implements OfferRef {
        public BoardIndex {
            if (index < 0) {
                throw new IllegalArgumentException(
                        "board index must be >= 0, got " + index
                                + " - a sentinel index is not a way to say 'not addressable'");
            }
        }
    }

    static OfferRef board(int index) {
        return new BoardIndex(index);
    }
}
