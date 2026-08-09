package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FS-3 / D-FSM-004 / D-FSM-008 / D-FSM-009 — face-API insert/extract. */
class FurnaceTransfersTest {

    private static final int BACKPACK_SIZE = 8;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** U-F4: failed face placement rolls back backpack and furnace. */
    @Test
    void uF4_insertRollsBackWhenOutputCannotFit() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 1));
        backpack.setItem(1, new ItemStack(Items.COAL, 1));
        // Fill every slot so charcoal cannot fit.
        for (int i = 2; i < BACKPACK_SIZE; i++) {
            backpack.setItem(i, new ItemStack(Items.DIRT));
        }

        FakeFurnaceContainer furnace = new FakeFurnaceContainer();
        assertFalse(FurnaceTransfers.tryInsert(
                backpack,
                furnace,
                new ItemStack(Items.OAK_LOG),
                new ItemStack(Items.COAL),
                new ItemStack(Items.CHARCOAL)));

        assertEquals(1, ScavengerCrafting.count(backpack, Items.OAK_LOG));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.COAL));
        assertTrue(FurnaceTransfers.isEmptyForNewJob(furnace));
    }

    @Test
    void insertSucceedsOnEmptyFurnaceAndReservesOutput() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        backpack.setItem(1, new ItemStack(Items.COAL, 1));

        FakeFurnaceContainer furnace = new FakeFurnaceContainer();
        assertTrue(FurnaceTransfers.tryInsert(
                backpack,
                furnace,
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT)));

        assertEquals(1, ScavengerCrafting.count(backpack, Items.RAW_IRON));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.COAL));
        assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
        assertTrue(furnace.getItem(1).is(Items.COAL));
        assertTrue(furnace.getItem(2).isEmpty());
    }

    /** U-F5: extract takes only matching job output; foreign stacks stay. */
    @Test
    void uF5_extractOnlyJobOwnedOutput() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        FakeFurnaceContainer furnace = new FakeFurnaceContainer();
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 1));

        Optional<ItemStack> got = FurnaceTransfers.tryExtract(
                backpack, furnace, StackFingerprint.of(Items.IRON_INGOT, 1), 1);
        assertTrue(got.isPresent());
        assertTrue(got.get().is(Items.IRON_INGOT));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.IRON_INGOT));
        assertTrue(furnace.getItem(2).isEmpty());

        // Pre-existing non-matching output must not be stolen.
        furnace.setItem(2, new ItemStack(Items.GOLD_INGOT, 1));
        assertTrue(FurnaceTransfers.tryExtract(
                        backpack, furnace, StackFingerprint.of(Items.IRON_INGOT, 1), 1)
                .isEmpty());
        assertTrue(furnace.getItem(2).is(Items.GOLD_INGOT));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.IRON_INGOT));
    }

    @Test
    void insertRejectsNonEmptyFurnace() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON));
        backpack.setItem(1, new ItemStack(Items.COAL));
        FakeFurnaceContainer furnace = new FakeFurnaceContainer();
        furnace.setItem(2, new ItemStack(Items.CHARCOAL));

        assertFalse(FurnaceTransfers.tryInsert(
                backpack,
                furnace,
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT)));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.RAW_IRON));
    }

    /** U-F10: fuel-face negotiation must find EAST without mutating rejected faces. */
    @Test
    void uF10_insertNegotiatesEastOnlyFuelFace() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON));
        backpack.setItem(1, new ItemStack(Items.COAL));
        FakeFurnaceContainer furnace = new FakeFurnaceContainer(Direction.EAST);

        assertTrue(FurnaceTransfers.tryInsert(
                backpack,
                furnace,
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT)));

        assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
        assertTrue(furnace.getItem(1).is(Items.COAL));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.RAW_IRON));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.COAL));
    }

    /** No acceptable horizontal fuel face must roll both inventories back completely. */
    @Test
    void insertRollsBackWhenNoHorizontalFuelFaceAccepts() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON));
        backpack.setItem(1, new ItemStack(Items.COAL));
        FakeFurnaceContainer furnace = FakeFurnaceContainer.rejectingAllFuelFaces();

        assertFalse(FurnaceTransfers.tryInsert(
                backpack,
                furnace,
                new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT)));

        assertEquals(1, ScavengerCrafting.count(backpack, Items.RAW_IRON));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.COAL));
        assertTrue(FurnaceTransfers.isEmptyForNewJob(furnace));
    }
}
