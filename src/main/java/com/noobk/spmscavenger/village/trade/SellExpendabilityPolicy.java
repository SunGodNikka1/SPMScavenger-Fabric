package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.FuelExpendability;
import net.minecraft.world.item.ItemStack;

/**
 * V2-D — how much of a material the mob may sell, computed fresh every time.
 *
 * <h2>One permission layer, two consumers</h2>
 *
 * Selling has the same shape as burning: <i>the backpack contains 20 wheat</i> is a fact about the
 * item, and <i>may I spend the wheat</i> is a fact about the mob's situation. That layer already
 * exists as {@link FuelExpendability}, so this class <b>delegates</b> rather than growing a second
 * predicate that would drift from the first (SPM-2, B-VR-92). Equipment is protected here for exactly
 * the reason it is protected from a furnace: durability marks an investment, not surplus.
 *
 * <h2>Reserve first, then sell</h2>
 *
 * Disposable is what remains <b>after</b> the mob keeps what it needs. A crafting reserve is not
 * spare merely because a villager will pay for it.
 *
 * <p>And the amount finally sold is bounded by the purchase, not by this number:
 * {@code TradeChainPolicy} asks for {@code ceil(deficit / perSell)} uses and no more.
 * <b>Disposable means permitted to spend, not desirable to spend.</b>
 */
public final class SellExpendabilityPolicy {

    private SellExpendabilityPolicy() {
    }

    /**
     * Units of {@code material} the mob may put on the counter.
     *
     * @param heldUnits how many the mob currently holds
     * @param reservedUnits how many an existing consumer or craft chain has already claimed
     * @param mainHand held items are in use, whatever they are
     * @return {@code 0} when the material is protected outright, otherwise the surplus
     */
    public static int disposableUnits(
            ItemStack material, int heldUnits, int reservedUnits,
            ItemStack mainHand, ItemStack offHand) {
        if (material == null || material.isEmpty() || heldUnits <= 0) {
            return 0;
        }
        // The same veto that stops a wooden pickaxe becoming furnace fuel stops it becoming stock.
        if (!FuelExpendability.mayBurn(material, mainHand, offHand)) {
            return 0;
        }
        return Math.max(0, heldUnits - Math.max(0, reservedUnits));
    }

    /**
     * How many sell uses that surplus can actually fund.
     *
     * @param unitsPerSellUse the offer's cost A count — how much leaves the backpack per trade
     */
    public static int affordableSellUses(int disposableUnits, int unitsPerSellUse) {
        if (disposableUnits <= 0 || unitsPerSellUse <= 0) {
            return 0;
        }
        return disposableUnits / unitsPerSellUse;
    }
}
