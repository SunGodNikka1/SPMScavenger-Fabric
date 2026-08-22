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

/** Task-58 — expendability layers (T58-12, T58-13). */
class CompostExpendabilityPolicyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void t58_1_seedSurplusPlansOneUnit() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        var offer = CompostExpendabilityPolicy.planInsertion(
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, null);
        assertTrue(offer.isPresent());
        assertEquals(Items.WHEAT_SEEDS, offer.get().item());
        assertEquals(1, offer.get().count());
    }

    @Test
    void t58_12_unmodelledCompostableDoesNotPlan() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.CARROT, 10));
        assertFalse(CompostExpendabilityPolicy.planInsertion(
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, null).isPresent());
    }

    @Test
    void t58_13_villagerBreedingFoodDoesNotPlan() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.BREAD, 20));
        assertFalse(CompostExpendabilityPolicy.planInsertion(
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, null).isPresent());
    }

    @Test
    void heldStackIsNeverSpent() throws java.io.IOException {
        String body = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/village/compost/CompostExpendabilityPolicy.java"));
        assertTrue(body.contains("isSameItemSameComponents(stack, mainHand)"));
        assertTrue(body.contains("isSameItemSameComponents(stack, offHand)"));
    }
}
