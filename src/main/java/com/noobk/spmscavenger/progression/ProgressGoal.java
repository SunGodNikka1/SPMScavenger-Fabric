package com.noobk.spmscavenger.progression;

import net.minecraft.world.item.Item;

/** Top-level progression target the planner works backward from. */
public enum ProgressGoal {
    TORCH_STOCK,
    WOODEN_PICKAXE,
    STONE_PICKAXE,
    IRON_PICKAXE,
    IRON_AXE,
    DIAMOND_PICKAXE,
    FURNACE_ITEM;

    public Item resultItem() {
        return switch (this) {
            case TORCH_STOCK -> net.minecraft.world.item.Items.TORCH;
            case WOODEN_PICKAXE -> net.minecraft.world.item.Items.WOODEN_PICKAXE;
            case STONE_PICKAXE -> net.minecraft.world.item.Items.STONE_PICKAXE;
            case IRON_PICKAXE -> net.minecraft.world.item.Items.IRON_PICKAXE;
            case IRON_AXE -> net.minecraft.world.item.Items.IRON_AXE;
            case DIAMOND_PICKAXE -> net.minecraft.world.item.Items.DIAMOND_PICKAXE;
            case FURNACE_ITEM -> net.minecraft.world.item.Items.FURNACE;
        };
    }
}
