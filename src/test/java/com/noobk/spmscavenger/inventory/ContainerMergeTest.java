package com.noobk.spmscavenger.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerMergeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void insertReturnsExactRemainder() {
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(Items.CARROT, 60));
        ItemStack toInsert = new ItemStack(Items.CARROT, 10);
        ItemStack remaining = ContainerMerge.insert(container, toInsert);
        assertTrue(remaining.isEmpty());
        assertEquals(64, container.getItem(0).getCount());
        assertEquals(6, container.getItem(1).getCount());
    }

    @Test
    void insertReportsUninsertedRemainder() {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
        ItemStack remaining = ContainerMerge.insert(container, new ItemStack(Items.WHEAT_SEEDS, 4));
        assertEquals(4, remaining.getCount());
    }

    @Test
    void removeReturnsTakenCount() {
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 1));
        container.setItem(1, new ItemStack(Items.BEETROOT_SEEDS, 2));
        int removed = ContainerMerge.remove(container, new ItemStack(Items.BEETROOT_SEEDS), 2);
        assertEquals(2, removed);
        assertEquals(1, container.getItem(1).getCount());
    }
}
