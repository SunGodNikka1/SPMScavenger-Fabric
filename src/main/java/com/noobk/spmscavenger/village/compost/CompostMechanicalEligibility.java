package com.noobk.spmscavenger.village.compost;

import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla mechanical compost eligibility — not spend authority (D-VR-086-A2).
 */
public final class CompostMechanicalEligibility {

    private CompostMechanicalEligibility() {}

    public static boolean isCompostable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        float chance = compostChance(stack.getItem());
        return chance > 0.0F;
    }

    private static float compostChance(Item item) {
        CompostingChanceRegistry registry = CompostingChanceRegistry.INSTANCE;
        if (registry != null) {
            Float chance = registry.get(item);
            if (chance != null) {
                return chance;
            }
        }
        return gate0VanillaChance(item);
    }

    /**
     * Offline/unit-test fallback when Fabric registries are not bootstrapped. Gate 0 pinned values
     * for gen-1 regression probes only — runtime uses {@link CompostingChanceRegistry}.
     */
    private static float gate0VanillaChance(Item item) {
        if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS) {
            return 0.30F;
        }
        if (item == Items.CARROT || item == Items.POTATO) {
            return 0.65F;
        }
        if (item == Items.BREAD) {
            return 0.85F;
        }
        return -1.0F;
    }

    public static boolean canAcceptInput(BlockState state) {
        return state != null
                && state.getBlock() instanceof ComposterBlock
                && state.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL;
    }
}
