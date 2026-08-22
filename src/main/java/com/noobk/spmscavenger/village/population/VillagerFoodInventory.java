package com.noobk.spmscavenger.village.population;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * Read-only villager breeding-food inventory points (mirrors private Villager logic).
 */
public final class VillagerFoodInventory {

    private VillagerFoodInventory() {}

    public static int inventoryFoodPoints(Villager villager) {
        if (villager == null) {
            return 0;
        }
        return Villager.FOOD_POINTS.entrySet().stream()
                .mapToInt(entry -> villager.getInventory().countItem(entry.getKey()) * entry.getValue())
                .sum();
    }

    public static boolean isBreedingFood(Item item) {
        return item != null && Villager.FOOD_POINTS.containsKey(item);
    }

    public static int foodPointsPerItem(Item item) {
        return Villager.FOOD_POINTS.getOrDefault(item, 0);
    }
}
