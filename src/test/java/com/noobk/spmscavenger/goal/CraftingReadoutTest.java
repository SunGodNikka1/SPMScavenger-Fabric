package com.noobk.spmscavenger.goal;

import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.ScavengerConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingReadoutTest {

    private static final Map<ScavengerCrafting.Step, String> EXPECTED = Map.ofEntries(
            Map.entry(ScavengerCrafting.Step.NOTHING, "Crafting"),
            Map.entry(ScavengerCrafting.Step.LOGS_TO_PLANKS, "Crafting — planks"),
            Map.entry(ScavengerCrafting.Step.PLANKS_TO_STICKS, "Crafting — sticks"),
            Map.entry(ScavengerCrafting.Step.MAKE_TORCHES, "Crafting — torches"),
            Map.entry(ScavengerCrafting.Step.MAKE_TABLE, "Crafting — crafting table"),
            Map.entry(ScavengerCrafting.Step.MAKE_PICKAXE, "Crafting — wooden pickaxe"),
            Map.entry(ScavengerCrafting.Step.MAKE_AXE, "Crafting — wooden axe"),
            Map.entry(ScavengerCrafting.Step.MAKE_STONE_PICKAXE, "Crafting — stone pickaxe"),
            Map.entry(ScavengerCrafting.Step.MAKE_STONE_AXE, "Crafting — stone axe"),
            Map.entry(ScavengerCrafting.Step.MAKE_IRON_PICKAXE, "Crafting — iron pickaxe"),
            Map.entry(ScavengerCrafting.Step.MAKE_IRON_AXE, "Crafting — iron axe"),
            Map.entry(ScavengerCrafting.Step.MAKE_DIAMOND_PICKAXE, "Crafting — diamond pickaxe"),
            Map.entry(ScavengerCrafting.Step.MAKE_DIAMOND_AXE, "Crafting — diamond axe"),
            Map.entry(ScavengerCrafting.Step.MAKE_CAMPFIRE, "Crafting — campfire"),
            Map.entry(ScavengerCrafting.Step.MAKE_FURNACE, "Crafting — furnace"));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyCraftingStepHasAnExplicitStableLabel() {
        assertEquals(ScavengerCrafting.Step.values().length, EXPECTED.size());
        for (ScavengerCrafting.Step step : ScavengerCrafting.Step.values()) {
            assertEquals(EXPECTED.get(step), CraftingReadout.forStep(step), step.name());
        }
    }

    @Test
    void readingTheSelectedRecipeDoesNotMutateTheCraftingInputs() {
        SimpleContainer backpack = new SimpleContainer(
                new ItemStack(Items.COAL), new ItemStack(Items.STICK));

        ScavengerCrafting.Step step = ScavengerCrafting.nextStep(backpack, new ScavengerConfig());
        assertEquals(ScavengerCrafting.Step.MAKE_TORCHES, step);
        assertEquals("Crafting — torches", CraftingReadout.forStep(step));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.STICK));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.TORCH));
    }
}
