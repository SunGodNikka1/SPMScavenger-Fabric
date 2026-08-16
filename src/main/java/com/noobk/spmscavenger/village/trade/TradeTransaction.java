package com.noobk.spmscavenger.village.trade;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * V2-A — staged inventory arithmetic. Pure: no entity, no world, and <b>no write to the real
 * container until {@link #commit}</b>.
 *
 * <h2>Why the payment cannot go through {@code MerchantOffer#take}</h2>
 *
 * {@code take(a, b)} shrinks <b>exactly the two stacks handed to it</b> and no others, because
 * {@code MerchantMenu} guarantees payment sits consolidated in two slots. The SPM backpack has eight
 * slots and no such guarantee — a 20-wheat cost is routinely {@code 16 + 4} across two of them. So
 * {@code take} cannot express the payment. {@code satisfiedBy} remains useful as validation; the
 * debit is ours to perform across slots.
 *
 * <h2>Why staging</h2>
 *
 * A trade is all-or-nothing. Debiting the real container and then discovering the result does not
 * fit would take the mob's items and give nothing back — an item-voiding bug that persists to the
 * save. Nothing real is touched until every step has already succeeded on a copy.
 */
public final class TradeTransaction {

    private TradeTransaction() {
    }

    /** A working copy of every slot. Nothing here is shared with the container. */
    public static ItemStack[] stage(Container container) {
        ItemStack[] staged = new ItemStack[container.getContainerSize()];
        for (int i = 0; i < staged.length; i++) {
            staged[i] = container.getItem(i).copy();
        }
        return staged;
    }

    /**
     * Remove {@code cost} from the staged slots, spending across as many as needed.
     *
     * <p>The affordability check runs <b>before</b> any shrink, so a failed debit leaves
     * {@code staged} untouched and cannot half-spend even within the staging array.
     *
     * @return {@code true} when the full amount was removed
     */
    public static boolean debit(ItemStack[] staged, ItemStack cost) {
        if (cost == null || cost.isEmpty()) {
            return true;
        }
        if (countMatching(staged, cost) < cost.getCount()) {
            return false;
        }
        int remaining = cost.getCount();
        for (int i = 0; i < staged.length && remaining > 0; i++) {
            if (!matches(staged[i], cost)) {
                continue;
            }
            int take = Math.min(remaining, staged[i].getCount());
            staged[i].shrink(take);
            if (staged[i].isEmpty()) {
                staged[i] = ItemStack.EMPTY;
            }
            remaining -= take;
        }
        return remaining == 0;
    }

    /**
     * Insert {@code result} into the staged slots, merging into partial stacks before opening new
     * ones, and respecting {@code getMaxStackSize}.
     *
     * @return {@code true} when all of it fit. A {@code false} return may leave {@code staged}
     *     partially filled, which is safe only because the caller discards the entire staging array
     *     on failure — it is never committed.
     */
    public static boolean insert(ItemStack[] staged, ItemStack result) {
        if (result == null || result.isEmpty()) {
            return true;
        }
        int remaining = result.getCount();
        int max = result.getMaxStackSize();

        for (int i = 0; i < staged.length && remaining > 0; i++) {
            if (staged[i].isEmpty() || !matches(staged[i], result)) {
                continue;
            }
            int room = max - staged[i].getCount();
            if (room <= 0) {
                continue;
            }
            int put = Math.min(room, remaining);
            staged[i].grow(put);
            remaining -= put;
        }
        for (int i = 0; i < staged.length && remaining > 0; i++) {
            if (!staged[i].isEmpty()) {
                continue;
            }
            int put = Math.min(max, remaining);
            staged[i] = result.copyWithCount(put);
            remaining -= put;
        }
        return remaining == 0;
    }

    /** The first and only mutation of anything real. */
    public static void commit(Container container, ItemStack[] staged) {
        for (int i = 0; i < staged.length; i++) {
            container.setItem(i, staged[i]);
        }
        container.setChanged();
    }

    public static int countMatching(ItemStack[] staged, ItemStack sample) {
        int total = 0;
        for (ItemStack stack : staged) {
            if (matches(stack, sample)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean matches(ItemStack candidate, ItemStack sample) {
        return candidate != null && !candidate.isEmpty()
                && ItemStack.isSameItemSameComponents(candidate, sample);
    }
}
