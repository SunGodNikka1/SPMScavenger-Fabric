package com.noobk.spmscavenger.village.crop;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deterministic harvest candidacy — never calls {@code Block.getDrops} (task-55 v2.1).
 */
public final class HarvestCandidatePolicy {

    private HarvestCandidatePolicy() {
    }

    public static boolean deterministicReplantFeasible(BlockState matureCrop, Container backpack) {
        if (!CropReplantSemantics.isMature(matureCrop)) {
            return false;
        }
        if (CropReplantSemantics.guaranteedPlantingDrop(matureCrop)) {
            return true;
        }
        Item planting = CropReplantSemantics.plantingItem(matureCrop);
        return ContainerMerge.count(backpack, new ItemStack(planting)) >= 1;
    }

    public static boolean isHarvestCandidate(
            boolean managedCell,
            BlockState cropState,
            Container backpack) {
        return managedCell
                && CropReplantSemantics.isMature(cropState)
                && deterministicReplantFeasible(cropState, backpack);
    }
}
