package com.noobk.spmscavenger.village;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * V4-A — a remembered output identity, not a remembered offer.
 *
 * <p>Count is deliberately normalized away. V2 decides live contribution from the current quote;
 * memory only retains the item-and-components identity already used by V2 quote correspondence.
 */
public final class TradeOutputCapability {

    private final ItemStack sample;

    private TradeOutputCapability(ItemStack sample) {
        this.sample = sample.copyWithCount(1);
    }

    public static TradeOutputCapability of(ItemStack output) {
        if (output == null || output.isEmpty()) {
            throw new IllegalArgumentException("capability output must be non-empty");
        }
        return new TradeOutputCapability(output);
    }

    public ItemStack sample() {
        return sample.copy();
    }

    public boolean matches(ItemStack output) {
        return output != null && !output.isEmpty()
                && ItemStack.isSameItemSameComponents(sample, output);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("output", sample.save(registries));
        return tag;
    }

    static TradeOutputCapability load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || !tag.contains("output")) {
            return null;
        }
        ItemStack output = ItemStack.parseOptional(registries, tag.getCompound("output"));
        return output.isEmpty() ? null : of(output);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TradeOutputCapability capability
                && ItemStack.isSameItemSameComponents(sample, capability.sample);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(sample);
    }

    @Override
    public String toString() {
        return Objects.toString(sample);
    }
}
