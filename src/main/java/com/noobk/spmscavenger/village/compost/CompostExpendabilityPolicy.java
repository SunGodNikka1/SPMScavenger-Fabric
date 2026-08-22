package com.noobk.spmscavenger.village.compost;

import com.noobk.spmscavenger.FuelExpendability;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.population.PlayerNutritionReserve;
import com.noobk.spmscavenger.village.population.PopulationFoodTuning;
import com.noobk.spmscavenger.village.population.VillagerFoodInventory;
import com.noobk.spmscavenger.village.trade.SellReserveModel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Composes protection layers for one-unit compost insertion (task-58 / D-VR-086-A2).
 *
 * <p>{@link CompostReserveModel} is the explicit gen-1 authority for wheat/beetroot seed surplus.
 * {@link com.noobk.spmscavenger.village.trade.SellReserveModel#reservedUnits} returning empty does
 * not authorize compost spend by itself, but it also must not veto items explicitly modelled by
 * {@link CompostReserveModel}. Unknown-to-compost materials remain fail-closed.
 */
public final class CompostExpendabilityPolicy {

    public record InsertionOffer(int slot, Item item, int count) {}

    private CompostExpendabilityPolicy() {}

    public static Optional<InsertionOffer> planInsertion(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        if (backpack == null) {
            return Optional.empty();
        }
        InsertionOffer best = null;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            ItemStack stack = backpack.getItem(slot);
            Optional<InsertionOffer> offer = planSlot(stack, slot, backpack, mainHand, offHand, cfg);
            if (offer.isEmpty()) {
                continue;
            }
            InsertionOffer candidate = offer.get();
            if (best == null || compareOffers(candidate, best, backpack) > 0) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    static Optional<InsertionOffer> planSlot(
            ItemStack stack,
            int slot,
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            ScavengerConfig cfg) {
        if (stack == null || stack.isEmpty() || !CompostMechanicalEligibility.isCompostable(stack)) {
            return Optional.empty();
        }
        if (!maySpendStack(stack, mainHand, offHand)) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        if (!CompostReserveModel.gen1Supported(item)) {
            return Optional.empty();
        }
        if (VillagerFoodInventory.isBreedingFood(item)) {
            return Optional.empty();
        }
        int perItemNutrition = PlayerNutritionReserve.nutritionOf(new ItemStack(item, 1));
        if (perItemNutrition > 0) {
            int totalNutrition = PlayerNutritionReserve.totalNutrition(backpack);
            if (totalNutrition - perItemNutrition < PopulationFoodTuning.MIN_SURVIVAL_NUTRITION_RESERVE) {
                return Optional.empty();
            }
        }
        if (cfg != null && SellReserveModel.modelled(stack, backpack, cfg)) {
            // Sell-reserve applies only to materials it models (logs/planks/sticks). Seeds are
            // unmodelled for sell and must fall through to CompostReserveModel — never vetoed here.
            var reserved = SellReserveModel.reservedUnits(stack, backpack, cfg);
            if (reserved.isEmpty()) {
                return Optional.empty();
            }
            int disposable = Math.max(0, stack.getCount() - reserved.getAsInt());
            if (disposable <= 0) {
                return Optional.empty();
            }
        }
        int compostDisposable = CompostReserveModel.disposableUnits(item, backpack);
        if (compostDisposable <= 0) {
            return Optional.empty();
        }
        return Optional.of(new InsertionOffer(slot, item, 1));
    }

    public static boolean slotStillDisposable(
            Container backpack,
            ItemStack mainHand,
            ItemStack offHand,
            int slot,
            Item item,
            ScavengerConfig cfg) {
        if (backpack == null || item == null || slot < 0 || slot >= backpack.getContainerSize()) {
            return false;
        }
        ItemStack stack = backpack.getItem(slot);
        if (stack.isEmpty() || stack.getItem() != item) {
            return false;
        }
        return planSlot(stack, slot, backpack, mainHand, offHand, cfg).isPresent();
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

    static int compareOffers(InsertionOffer left, InsertionOffer right, Container backpack) {
        int surplusLeft = CompostReserveModel.disposableUnits(left.item(), backpack);
        int surplusRight = CompostReserveModel.disposableUnits(right.item(), backpack);
        if (surplusLeft != surplusRight) {
            return Integer.compare(surplusLeft, surplusRight);
        }
        return Integer.compare(right.slot(), left.slot());
    }
}
