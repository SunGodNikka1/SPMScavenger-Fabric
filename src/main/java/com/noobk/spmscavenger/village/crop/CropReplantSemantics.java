package com.noobk.spmscavenger.village.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;

import java.util.Optional;

/**
 * Vanilla-only crop semantics for V3-C (task-55). SPM {@code ForagePolicy} is not imported.
 */
public final class CropReplantSemantics {

    public enum CropKind {
        WHEAT(Blocks.WHEAT, Items.WHEAT_SEEDS, false),
        CARROT(Blocks.CARROTS, Items.CARROT, true),
        POTATO(Blocks.POTATOES, Items.POTATO, true),
        BEETROOT(Blocks.BEETROOTS, Items.BEETROOT_SEEDS, false);

        private final Block cropBlock;
        private final Item plantingItem;
        /** Gate 0: loot pool guarantees at least one plantable item on mature break. */
        private final boolean guaranteedPlantingDrop;

        CropKind(Block cropBlock, Item plantingItem, boolean guaranteedPlantingDrop) {
            this.cropBlock = cropBlock;
            this.plantingItem = plantingItem;
            this.guaranteedPlantingDrop = guaranteedPlantingDrop;
        }

        public Block cropBlock() {
            return cropBlock;
        }

        public Item plantingItem() {
            return plantingItem;
        }

        public boolean guaranteedPlantingDrop() {
            return guaranteedPlantingDrop;
        }
    }

    private CropReplantSemantics() {
    }

    public static Optional<CropKind> kindOf(BlockState state) {
        if (state == null) {
            return Optional.empty();
        }
        Block block = state.getBlock();
        for (CropKind kind : CropKind.values()) {
            if (block == kind.cropBlock) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public static boolean supportedCrop(BlockState state) {
        return kindOf(state).isPresent();
    }

    public static boolean isMature(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return false;
        }
        return crop.isMaxAge(state);
    }

    public static boolean hasValidFarmlandSupport(LevelReader level, BlockState cropState, BlockPos cropPos) {
        if (!supportedCrop(cropState)) {
            return false;
        }
        return level.getBlockState(cropPos.below()).getBlock() instanceof FarmBlock;
    }

    public static BlockState ageZero(BlockState matureState) {
        CropKind kind = kindOf(matureState).orElseThrow();
        CropBlock crop = (CropBlock) kind.cropBlock();
        return crop.getStateForAge(0);
    }

    public static Item plantingItem(BlockState state) {
        return kindOf(state).map(CropKind::plantingItem).orElse(Items.AIR);
    }

    public static boolean guaranteedPlantingDrop(BlockState state) {
        return kindOf(state).map(CropKind::guaranteedPlantingDrop).orElse(false);
    }

    public static boolean isReplantMaterial(BlockState cropState, ItemStack drop) {
        if (drop.isEmpty()) {
            return false;
        }
        return kindOf(cropState)
                .map(kind -> drop.is(kind.plantingItem()))
                .orElse(false);
    }

    public static boolean isFoodOutput(BlockState cropState, ItemStack drop) {
        if (drop.isEmpty() || isReplantMaterial(cropState, drop)) {
            return false;
        }
        Optional<CropKind> kind = kindOf(cropState);
        if (kind.isEmpty()) {
            return false;
        }
        return switch (kind.get()) {
            case WHEAT -> drop.is(Items.WHEAT);
            case BEETROOT -> drop.is(Items.BEETROOT);
            case CARROT, POTATO -> false;
        };
    }
}
