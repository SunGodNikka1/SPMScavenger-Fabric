package com.noobk.spmscavenger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight item identity + count for furnace job reclaim checks (D-FSM-007).
 *
 * <p>Deliberately not a full NBT ItemStack snapshot — components beyond item/count are out of scope
 * for scavenger-inserted vanilla ores/logs/fuels.
 */
public record StackFingerprint(ResourceLocation itemId, int count) {

    public static StackFingerprint of(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new StackFingerprint(id, stack.getCount());
    }

    public static StackFingerprint of(Item item, int count) {
        return new StackFingerprint(BuiltInRegistries.ITEM.getKey(item), count);
    }

    public boolean matchesItem(ItemStack stack) {
        if (stack.isEmpty() || count <= 0) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Objects.equals(itemId, id);
    }

    public boolean matchesExact(ItemStack stack) {
        return matchesItem(stack) && stack.getCount() == count;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", itemId.toString());
        tag.putInt("Count", count);
        return tag;
    }

    public static Optional<StackFingerprint> load(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new StackFingerprint(id, tag.getInt("Count")));
    }
}
