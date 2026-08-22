package com.noobk.spmscavenger.village.population;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * PlayerMob edible nutrition accounting for survival reserve (task-57 v1.2).
 *
 * <p>Uses {@link net.minecraft.world.food.FoodProperties#nutrition} — not
 * {@link net.minecraft.world.entity.npc.Villager#FOOD_POINTS}.
 */
public final class PlayerNutritionReserve {

    private PlayerNutritionReserve() {}

    public static int totalNutrition(Container backpack) {
        if (backpack == null) {
            return 0;
        }
        int sum = 0;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            sum += nutritionOf(backpack.getItem(slot));
        }
        return sum;
    }

    public static int nutritionOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return 0;
        }
        return food.nutrition() * stack.getCount();
    }

    /**
     * Nutrition that may leave the backpack while keeping at least
     * {@link PopulationFoodTuning#MIN_SURVIVAL_NUTRITION_RESERVE}.
     */
    public static int disposableNutritionAfterRemoval(Container backpack, int nutritionRemoved) {
        int total = totalNutrition(backpack);
        int remaining = total - nutritionRemoved;
        if (remaining < PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE) {
            return 0;
        }
        return total - PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE;
    }
}
