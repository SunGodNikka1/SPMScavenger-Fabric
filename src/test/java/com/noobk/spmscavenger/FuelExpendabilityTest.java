package com.noobk.spmscavenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * FS-R2 — burnable is not expendable.
 *
 * <p>Reproduces the observed defect: a PlayerMob with a wooden pickaxe in a furnace's fuel slot,
 * chosen over the logs it was carrying, because vanilla marks wooden tools as fuel and the ranking
 * preferred a small non-log burn.
 */
class FuelExpendabilityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Deliberately a method, not a {@code static final} field.
     *
     * <p>{@code ItemStack.EMPTY} touches the item registry, so binding it in a static initialiser
     * runs <em>before</em> {@code @BeforeAll} and throws {@code NoClassDefFoundError}. As a field it
     * passed only while some other test class happened to bootstrap Minecraft first — running a
     * negative control in isolation is what exposed the order dependence.
     */
    private static ItemStack none() {
        return ItemStack.EMPTY;
    }

    /** The screenshot. A wooden pickaxe is valid vanilla fuel and must still never be spent. */
    @Test
    void mustNotHappen_aWoodenPickaxeIsTreatedAsFuel() {
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        assertTrue(pickaxe.isDamageableItem(), "sanity: vanilla gives it durability");
        assertFalse(FuelExpendability.mayBurn(pickaxe, none(), none()));
    }

    /**
     * The protection is durability, not a list of tool classes — so it holds for equipment nobody
     * remembered to enumerate, including modded equipment.
     */
    @Test
    void mustNotHappen_anyDurableEquipmentIsSpentAsFuel() {
        for (Item item : new Item[] {
                Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE,
                Items.WOODEN_SWORD, Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.FISHING_ROD,
                Items.LEATHER_HELMET, Items.ELYTRA, Items.FLINT_AND_STEEL}) {
            assertFalse(FuelExpendability.mayBurn(new ItemStack(item), none(), none()),
                    item + " has durability and must never be fuel");
        }
    }

    /** …while ordinary wooden junk stays expendable. Protecting everything is its own failure. */
    @Test
    void mustHappen_ordinaryBurnableJunkStaysExpendable() {
        for (Item item : new Item[] {
                Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK, Items.CRAFTING_TABLE,
                Items.OAK_SAPLING, Items.COAL, Items.CHARCOAL, Items.OAK_SLAB}) {
            assertTrue(FuelExpendability.mayBurn(new ItemStack(item, 8), none(), none()),
                    item + " is not equipment and must remain usable as fuel");
        }
    }

    /**
     * A stack the mob is presently holding is in use even when it is not equipment — taking planks
     * out from under the current task is a different bug, not a fuel decision.
     */
    @Test
    void mustNotHappen_theHeldStackIsBurned() {
        ItemStack planks = new ItemStack(Items.OAK_PLANKS, 8);
        assertTrue(FuelExpendability.mayBurn(planks, none(), none()), "expendable in the backpack");
        assertFalse(FuelExpendability.mayBurn(planks, new ItemStack(Items.OAK_PLANKS), none()),
                "and not while it is in the main hand");
        assertFalse(FuelExpendability.mayBurn(planks, none(), new ItemStack(Items.OAK_PLANKS)),
                "nor the off hand");
        assertTrue(FuelExpendability.isInUse(planks, new ItemStack(Items.OAK_PLANKS), none()),
                "and the reason is reportable separately from 'it is equipment'");
    }

    @Test
    void mustNotHappen_emptyOrNullIsOfferedAsFuel() {
        assertFalse(FuelExpendability.mayBurn(ItemStack.EMPTY, none(), none()));
        assertFalse(FuelExpendability.mayBurn(null, none(), none()));
    }

    /** The datapack extension point exists beside the derived rule, not instead of it. */
    @Test
    void mustHappen_aDatapackCanProtectWhatDurabilityCannotSee() {
        assertEquals("spmscavenger", FuelExpendability.NEVER_FUEL.location().getNamespace());
        assertEquals("never_fuel", FuelExpendability.NEVER_FUEL.location().getPath());
    }

    /**
     * End to end through the real ranking, with the inventory from the screenshot: a wooden pickaxe
     * and spare logs. Before the gate the pickaxe won — it is a non-log with just enough burn time,
     * and the comparator prefers exactly that.
     */
    @Test
    void mustHappen_theRankingPicksALogOverThePickaxe() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        backpack.setItem(1, new ItemStack(Items.OAK_LOG, 8));

        FurnacePolicy.FuelLookup fuels = stack -> {
            if (stack.is(Items.WOODEN_PICKAXE)) {
                return 200;
            }
            return stack.is(Items.OAK_LOG) ? 300 : 0;
        };

        ItemStack chosen = FurnacePolicy.chooseFuel(
                        backpack, new ScavengerConfig(), FurnacePolicy.SmeltDemand.IRON,
                        ItemStack.EMPTY, 200, fuels, ItemStack.EMPTY, ItemStack.EMPTY)
                .orElseThrow(() -> new AssertionError("a log was available and must be chosen"));

        assertTrue(chosen.is(Items.OAK_LOG),
                "chose " + chosen + " - the pickaxe is cheaper by burn time, which is precisely why "
                        + "ranking must run after expendability rather than before it");
    }
}
