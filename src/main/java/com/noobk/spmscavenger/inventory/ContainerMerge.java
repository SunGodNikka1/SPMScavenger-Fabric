package com.noobk.spmscavenger.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla {@link Container} merge helpers with explicit remainder semantics (task-55).
 */
public final class ContainerMerge {

    private ContainerMerge() {
    }

    /**
     * Merges into existing stacks, then empty slots. Returns the exact uninserted remainder.
     */
    public static ItemStack insert(Container container, ItemStack stack) {
        if (container == null || stack.isEmpty()) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int room = Math.min(
                        existing.getMaxStackSize() - existing.getCount(),
                        remaining.getCount());
                if (room > 0) {
                    existing.grow(room);
                    remaining.shrink(room);
                    container.setChanged();
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, remaining.copy());
                container.setChanged();
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    public static int count(Container container, ItemStack template) {
        if (container == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Removes up to {@code count} matching items. Returns how many were removed.
     */
    public static int remove(Container container, ItemStack template, int count) {
        if (container == null || template.isEmpty() || count <= 0) {
            return 0;
        }
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize() && removed < count; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int take = Math.min(stack.getCount(), count - removed);
            stack.shrink(take);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            removed += take;
            container.setChanged();
        }
        return removed;
    }
}
