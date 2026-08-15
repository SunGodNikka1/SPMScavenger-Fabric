package com.noobk.spmscavenger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * FS-R2 — <b>burnable is not expendable.</b>
 *
 * <h2>The defect this closes (`RUNTIME_CONFIRMED`)</h2>
 *
 * A PlayerMob was observed with a <b>wooden pickaxe in the fuel slot</b>. Nothing was broken in the
 * arithmetic: vanilla marks wooden tools as furnace fuel, {@code chooseFuel} asked
 * {@code AbstractFurnaceBlockEntity.isFuel(stack)}, and its ranking is <i>non-log first, then the
 * smallest burn time that suffices</i>. A wooden pickaxe is a non-log fuel with just enough burn
 * time for a 200-tick smelt, so it sorted to the front and won — over the logs it was standing next
 * to.
 *
 * <p>The policy was answering the wrong question. {@code isFuel} says "will this combust", which is
 * a fact about the item. Whether the mob may <em>spend</em> it is a fact about the mob's situation,
 * and nothing was asking it.
 *
 * <pre>
 * vanilla says burnable
 *         |
 *         v
 * may I sacrifice this?      &lt;-- this layer did not exist
 *         |
 *   +-----+------+
 *   |            |
 * PROTECTED   EXPENDABLE
 * </pre>
 *
 * <p>Same shape as an invariant this project has already paid for once: <b>preference does not create
 * permission</b>. Fuel value may rank only items that are already legally expendable; it may never
 * make an item expendable by being attractive.
 *
 * <h2>The predicate is derived, not enumerated</h2>
 *
 * The protection is {@link ItemStack#isDamageableItem()}, not a list of tool classes. A tool is a
 * thing with durability — that covers every pickaxe, axe, shovel, hoe, sword, bow, crossbow, shield,
 * fishing rod, flint and steel, elytra and armour piece in the game, <b>and every modded one</b>,
 * with no list to maintain and nothing to forget. Planks, sticks, logs, boats, crafting tables and
 * saplings are not damageable and stay expendable.
 *
 * <p>Beside it, and not instead of it, sits {@link #NEVER_FUEL} — a {@code required: false} tag so a
 * datapack can protect something the derived rule cannot see (a non-damageable progression item, a
 * modded quest reward). The derived predicate is the default; the tag is the extension point.
 */
public final class FuelExpendability {

    /**
     * {@code #spmscavenger:never_fuel} — datapack-extensible protection for items the durability rule
     * cannot see. Deliberately empty by default: an empty tag that nobody has to maintain beats a
     * list that silently rots.
     */
    public static final TagKey<Item> NEVER_FUEL = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "never_fuel"));

    private FuelExpendability() {
    }

    /**
     * Whether the mob may spend this stack as fuel.
     *
     * <p>Ordering matters for readability, not for correctness — every clause is a veto.
     *
     * @param mainHand the mob's current main-hand item, never burned even when it is not a tool: it
     *     is the thing the mob is presently using, and taking it mid-task is its own class of bug
     * @param offHand as {@code mainHand}
     */
    public static boolean mayBurn(ItemStack stack, ItemStack mainHand, ItemStack offHand) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // Tools, weapons, armour, shields, bows - anything with durability is an investment the mob
        // made, not a log it picked up. Covers modded equipment for free.
        if (stack.isDamageableItem()) {
            return false;
        }
        if (stack.is(NEVER_FUEL)) {
            return false;
        }
        // Held items are in use. A stack of planks in hand is ordinarily expendable, but the mob is
        // holding it for a reason and pulling it out from under the current task is a separate defect.
        if (isSameItem(stack, mainHand) || isSameItem(stack, offHand)) {
            return false;
        }
        // Craft-chain reserves (the log reserve that keeps enough wood for planks and sticks) are NOT
        // duplicated here. They already live in FurnacePolicy.chooseFuel, they are quantity-based
        // rather than item-based, and a second copy would drift from the first (SPM-2).
        return true;
    }

    /**
     * Whether the item is protected specifically because the mob is wearing or wielding it.
     *
     * <p>Separate from {@link #mayBurn} so a readout can say <i>which</i> reason applied — "it is my
     * pickaxe" and "it is in my hand" are different explanations of the same refusal.
     */
    public static boolean isInUse(ItemStack stack, ItemStack mainHand, ItemStack offHand) {
        return isSameItem(stack, mainHand) || isSameItem(stack, offHand);
    }

    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItem(a, b);
    }
}
