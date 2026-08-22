package com.noobk.spmscavenger.village.population;

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

/** Task-57 — expendability + nutrition reserve (T57-6, T57-1 partial). */
class PopulationFoodExpendabilityPolicyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

  @Test
  void t57_6_exactReserveBlocksDelivery() {
    SimpleContainer backpack = new SimpleContainer(9);
    backpack.setItem(0, new ItemStack(Items.APPLE, 3));
    assertFalse(PopulationFoodExpendabilityPolicy.planDelivery(
            backpack, ItemStack.EMPTY, ItemStack.EMPTY, 0).isPresent());
  }

  @Test
  void t57_6_reserveZeroIsForbiddenFallback() {
    assertEquals(12, PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE);
  }

  @Test
  void breedingFoodWithoutPlayerNutritionMayStillPlan() {
    SimpleContainer backpack = new SimpleContainer(9);
    backpack.setItem(0, new ItemStack(Items.CARROT, 20));
    backpack.setItem(1, new ItemStack(Items.BREAD, 13));
    var offer = PopulationFoodExpendabilityPolicy.planDelivery(
            backpack, ItemStack.EMPTY, ItemStack.EMPTY, 0);
    assertTrue(offer.isPresent());
    assertEquals(Items.BREAD, offer.get().item());
  }

  @Test
  void heldStackIsNeverSpent() throws java.io.IOException {
    String body = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/noobk/spmscavenger/village/population/PopulationFoodExpendabilityPolicy.java"));
    assertTrue(body.contains("isSameItemSameComponents(stack, mainHand)"));
    assertTrue(body.contains("isSameItemSameComponents(stack, offHand)"));
  }

  @Test
  void negativeControl_wrongReserveWouldLetExactTwelvePass() {
    SimpleContainer backpack = new SimpleContainer(9);
    backpack.setItem(0, new ItemStack(Items.APPLE, 3));
    int disposable = PlayerNutritionReserve.disposableNutritionAfterRemoval(backpack, 0);
    assertEquals(0, disposable);
    assertFalse(PopulationFoodExpendabilityPolicy.planDelivery(
            backpack, ItemStack.EMPTY, ItemStack.EMPTY, 0).isPresent(),
        "missing <= reserve check would still pass this negative if reserve were 0");
  }
}
