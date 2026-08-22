package com.noobk.spmscavenger.village.compost;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Gen-1 primary compost spend authority — wheat/beetroot seed surplus after replant reserve.
 *
 * <p>{@link com.noobk.spmscavenger.village.trade.SellReserveModel} empty for seeds is correct
 * fail-closed for <em>sell</em> funding only; compost spend for supported seeds is owned here.
 */
public final class CompostReserveModel {

    /** Managed harvest replant semantics require at least one inventory seed (task-55). */
    public static final int REPLANT_RESERVE_PER_KIND = 1;

    private CompostReserveModel() {}

    public static boolean gen1Supported(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS;
    }

    public static int disposableUnits(Item item, Container backpack) {
        if (backpack == null || !gen1Supported(item)) {
            return 0;
        }
        int held = ContainerMerge.count(backpack, new net.minecraft.world.item.ItemStack(item));
        return Math.max(0, held - REPLANT_RESERVE_PER_KIND);
    }
}
