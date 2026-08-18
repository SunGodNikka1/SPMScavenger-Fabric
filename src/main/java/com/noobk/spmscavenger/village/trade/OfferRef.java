package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.item.ItemStack;

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
 * index to hold. Sealing makes "this ref is not addressable" a compile-time fact
 * rather than a sentinel value like {@code 9_999} that type-checks fine and then indexes into
 * somebody's offer list. Both variants now exist, so {@code VillagerTradeAdapter}'s
 * {@code instanceof BoardIndex} check is a live guard rather than a formality — a {@link Requote}
 * reaching board resolution is refused, not addressed.
 *
 * <p>Deliberately <b>no</b> {@code tieBreak()} method. Deriving the round ordinal from the ref is
 * how the two meanings got merged in the first place.
 */
public sealed interface OfferRef permits OfferRef.BoardIndex, OfferRef.Requote {

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

    /**
     * An offer with <b>no address</b>, re-resolved by asking its source to quote the same input
     * again.
     *
     * <h2>Identity is item + components, never count</h2>
     *
     * Trade Everything's own repricer settles this: it compares its remembered input with
     * {@code ItemStack.isSameItemSameComponents} and stores it as {@code input.copyWithCount(1)}
     * (`CONFIRMED` from {@code MerchantContainerMixin}). Its price is a function of the item and its
     * components; the held count decides how many uses are affordable, which is inventory's business
     * and not identity's.
     *
     * <p>So {@code 64x oak_log} and {@code 42x oak_log} are the <b>same</b> requote key, and the
     * count is canonicalized to 1 on the way in. Letting quantity into identity here would
     * re-create the conflation step 2 removed, one layer down.
     *
     * <h2>Why equals and hashCode are hand-written</h2>
     *
     * {@code ItemStack} inherits <b>identity</b> equality, so the record's generated {@code equals}
     * would call two independently copied but component-identical stacks different. That is not a
     * data-class quirk: {@code SellFundingLeg.covers} compares {@code attempted.ref().equals(...)}
     * at the execution boundary, so a wrong {@code equals} here becomes a mob refusing its own
     * authorized trade — exactly the step-2 regression, arriving by a different road.
     *
     * <h2>Defensively immutable</h2>
     *
     * The stack is copied in and copied out. A caller mutating the stack it passed, or the stack it
     * received from {@link #inputKey()}, cannot reach the identity this ref stores.
     */
    record Requote(ItemStack inputKey) implements OfferRef {
        public Requote {
            if (inputKey == null || inputKey.isEmpty()) {
                throw new IllegalArgumentException(
                        "a requote key needs an input - an empty stack identifies no offer");
            }
            inputKey = inputKey.copyWithCount(1);
        }

        /** A copy, so the caller cannot reach the stored identity. */
        @Override
        public ItemStack inputKey() {
            return inputKey.copy();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Requote requote
                            && ItemStack.isSameItemSameComponents(inputKey, requote.inputKey));
        }

        /** Consistent with {@link #equals}: the same item-and-components hash it compares on. */
        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(inputKey);
        }

        @Override
        public String toString() {
            return "Requote[" + inputKey.getItem() + " " + inputKey.getComponents() + "]";
        }
    }

    static OfferRef board(int index) {
        return new BoardIndex(index);
    }

    /** Count is canonicalized away; pass the stack as held. */
    static OfferRef requote(ItemStack input) {
        return new Requote(input);
    }
}
