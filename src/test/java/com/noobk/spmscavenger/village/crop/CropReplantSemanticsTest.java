package com.noobk.spmscavenger.village.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CropReplantSemanticsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void immatureCarrotIsSupportedButNotMature() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState immature = carrots.getStateForAge(2);
        assertTrue(CropReplantSemantics.supportedCrop(immature));
        assertFalse(CropReplantSemantics.isMature(immature));
    }

    @Test
    void wheatIncludedInSupportedSet() {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockState mature = wheat.getStateForAge(wheat.getMaxAge());
        assertTrue(CropReplantSemantics.supportedCrop(mature));
        assertTrue(CropReplantSemantics.isMature(mature));
    }

    @Test
    void ageZeroPreservesCropKind() {
        CropBlock beetroots = (CropBlock) Blocks.BEETROOTS;
        BlockState mature = beetroots.getStateForAge(beetroots.getMaxAge());
        BlockState ageZero = CropReplantSemantics.ageZero(mature);
        assertEquals(0, beetroots.getAge(ageZero));
        assertTrue(CropReplantSemantics.supportedCrop(ageZero));
    }

    @Test
    void guaranteedDropFlagsMatchGate0() {
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        BlockState matureCarrot = carrots.getStateForAge(carrots.getMaxAge());
        assertTrue(CropReplantSemantics.guaranteedPlantingDrop(matureCarrot));

        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockState matureWheat = wheat.getStateForAge(wheat.getMaxAge());
        assertFalse(CropReplantSemantics.guaranteedPlantingDrop(matureWheat));
    }
}
