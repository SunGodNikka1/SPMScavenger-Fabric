package com.noobk.spmscavenger.village.compost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Task-58 — CompostReserveModel (T58-6, T58-12). */
class CompostReserveModelTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void t58_6_replantReserveBlocksExactOneSeed() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 1));
        assertEquals(0, CompostReserveModel.disposableUnits(Items.WHEAT_SEEDS, backpack));
    }

    @Test
    void t58_6_surplusAfterReplantReserveIsDisposable() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        assertEquals(2, CompostReserveModel.disposableUnits(Items.WHEAT_SEEDS, backpack));
    }

    @Test
    void t58_12_unmodelledCompostableReturnsZero() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.CARROT, 10));
        assertEquals(0, CompostReserveModel.disposableUnits(Items.CARROT, backpack));
        assertFalse(CompostReserveModel.gen1Supported(Items.CARROT));
    }

    @Test
    void negativeControl_zeroReserveWouldLetExactOnePass() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 1));
        assertTrue(CompostReserveModel.disposableUnits(Items.BEETROOT_SEEDS, backpack) == 0,
                "missing replant reserve would still pass this negative if reserve were 0");
    }
}
