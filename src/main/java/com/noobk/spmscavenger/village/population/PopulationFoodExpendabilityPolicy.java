package com.noobk.spmscavenger.village.population;

import com.noobk.spmscavenger.FuelExpendability;
import com.noobk.spmscavenger.inventory.ContainerMerge;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.Optional;

/**
 * Disposable villager breeding-food authority (task-57 G0-6).
 */
public final class PopulationFoodExpendabilityPolicy {

    public record DeliveryOffer(int slot, Item item, int count, int villagerFoodValue) {}

    private PopulationFoodExpendabilityPolicy() {}

    public static Optional<DeliveryOffer> planDelivery(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            int recipientInventoryFoodPoints) {
        if (backpack == null) {
            return Optional.empty();
        }
        int meaningfulBudget = meaningfulProgressBudget(recipientInventoryFoodPoints);
        if (meaningfulBudget <= 0) {
            return Optional.empty();
        }
        int totalNutrition = PlayerNutritionReserve.totalNutrition(backpack);
        if (totalNutrition <= PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE) {
            return Optional.empty();
        }

        DeliveryOffer best = null;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack stack = backpack.getItem(slot);
            if (stack.isEmpty() || !VillagerFoodInventory.isBreedingFood(stack.getItem())) {
                continue;
            }
            if (!maySpendStack(stack, mainHand, offHand)) {
                continue;
            }
            int perItemNutrition = PlayerNutritionReserve.nutritionOf(new ItemStack(stack.getItem(), 1));
            int perItemFoodValue = VillagerFoodInventory.foodPointsPerItem(stack.getItem());
            if (perItemFoodValue <= 0) {
                continue;
            }
            int maxByNutrition = maxRemovableCountForNutrition(
                    totalNutrition, perItemNutrition, stack.getCount());
            int maxByFoodValue = (meaningfulBudget + perItemFoodValue - 1) / perItemFoodValue;
            int count = Math.min(stack.getCount(), Math.min(maxByNutrition, maxByFoodValue));
            if (count <= 0) {
                continue;
            }
            int foodValue = count * perItemFoodValue;
            DeliveryOffer offer = new DeliveryOffer(slot, stack.getItem(), count, foodValue);
            if (best == null || offer.villagerFoodValue() > best.villagerFoodValue()) {
                best = offer;
            }
        }
        return Optional.ofNullable(best);
    }

    public static int disposableVillagerFoodValue(Container backpack, ItemStack mainHand, ItemStack offHand) {
        return planDelivery(backpack, mainHand, offHand, 0)
                .map(DeliveryOffer::villagerFoodValue)
                .orElse(0);
    }

    private static int meaningfulProgressBudget(int recipientInventoryFoodPoints) {
        int towardWillingness = Math.max(0, 12 - recipientInventoryFoodPoints);
        return Math.min(
                towardWillingness,
                PopulationFoodTuning.MAX_EPISODE_FOOD_VALUE);
    }

    private static boolean maySpendStack(ItemStack stack, ItemStack mainHand, ItemStack offHand) {
        if (stack.is(FuelExpendability.NEVER_FUEL)) {
            return false;
        }
        if (stack.isDamageableItem()) {
            return false;
        }
        if (ItemStack.isSameItemSameComponents(stack, mainHand)
                || ItemStack.isSameItemSameComponents(stack, offHand)) {
            return false;
        }
        return true;
    }

    private static int maxRemovableCountForNutrition(
            int totalNutrition, int perItemNutrition, int stackCount) {
        if (perItemNutrition <= 0) {
            // Breeding food with no player nutrition does not debit the edible reserve pool.
            return stackCount;
        }
        int maxRemovableNutrition = totalNutrition - PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE;
        if (maxRemovableNutrition <= 0) {
            return 0;
        }
        int maxCount = maxRemovableNutrition / perItemNutrition;
        return Math.min(stackCount, maxCount);
    }

    /** Pick best offer when multiple slots compete — stable slot order tie-break. */
    public static Optional<DeliveryOffer> selectBestOffer(java.util.List<DeliveryOffer> offers) {
        return offers.stream()
                .max(Comparator.comparingInt(DeliveryOffer::villagerFoodValue)
                        .thenComparingInt(DeliveryOffer::slot));
    }
}
