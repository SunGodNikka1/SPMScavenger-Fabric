package com.noobk.spmscavenger.village.population;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Task-57 — handoff kernel abort paths (T57-13, T57-8 partial). */
class PopulationFoodHandoffBehaviorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void t57_13_mobGriefingFalsePerformsNoDebit() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.CARROT, 10));
        int before = ContainerMerge.count(backpack, new ItemStack(Items.CARROT));
        PopulationFoodHandoff.CommitResult result = PopulationFoodHandoff.commitKernel(
                null,
                null,
                null,
                backpack,
                Items.CARROT,
                3,
                false);
        assertEquals(PopulationFoodHandoff.CommitOutcome.ABORT, result.outcome());
        assertEquals(before, ContainerMerge.count(backpack, new ItemStack(Items.CARROT)));
    }

    @Test
    void nullKernelArgsAbortWithoutDebit() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.CARROT, 5));
        PopulationFoodHandoff.commitKernel(null, null, null, backpack, Items.CARROT, 1, true);
        assertEquals(5, ContainerMerge.count(backpack, new ItemStack(Items.CARROT)));
    }

    @Test
    void negativeControl_griefingFlagMustGateDebit() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.CARROT, 5));
        PopulationFoodHandoff.CommitResult withoutGriefing = PopulationFoodHandoff.commitKernel(
                null, null, null, backpack, Items.CARROT, 2, false);
        assertEquals(PopulationFoodHandoff.CommitOutcome.ABORT, withoutGriefing.outcome());
        assertEquals(5, ContainerMerge.count(backpack, new ItemStack(Items.CARROT)),
            "a missing mobGriefing gate would debit here");
  }
}
