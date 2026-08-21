package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HarvestCandidatePolicyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void carrotMatureDoesNotRequireHeldSeed() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState mature = carrots.getStateForAge(carrots.getMaxAge());
        SimpleContainer empty = new SimpleContainer(9);
        assertTrue(HarvestCandidatePolicy.deterministicReplantFeasible(mature, empty));
    }

    @Test
    void wheatRequiresHeldSeedWithoutSamplingDrops() {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockState mature = wheat.getStateForAge(wheat.getMaxAge());
        SimpleContainer empty = new SimpleContainer(9);
        assertFalse(HarvestCandidatePolicy.deterministicReplantFeasible(mature, empty));

        SimpleContainer withSeed = new SimpleContainer(9);
        withSeed.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 1));
        assertTrue(HarvestCandidatePolicy.deterministicReplantFeasible(mature, withSeed));
    }

    @Test
    void immatureManagedCellIsNotHarvestCandidate() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState immature = carrots.getStateForAge(1);
        SimpleContainer backpack = new SimpleContainer(9);
        assertFalse(HarvestCandidatePolicy.isHarvestCandidate(true, immature, backpack));
    }
}
