package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Bootstrap#bootStrap()} binds item tags empty (no datapack), so recipes that count
 * {@code ItemTags.PLANKS} / {@code LOGS} cannot be end-to-end tested here — same constraint as
 * SPM's {@code ItemPickupPolicyTest}. TT-0R coverage uses concrete-item recipes (torches, stone
 * tools).
 */
class ScavengerCraftingTest {

    private static final int BACKPACK_SIZE = 8;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void canGiveRejectsPickaxeWhenEverySlotIsOccupied() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.COAL));
        backpack.setItem(3, new ItemStack(Items.TORCH));
        backpack.setItem(4, new ItemStack(Items.OAK_LOG));
        backpack.setItem(5, new ItemStack(Items.DIRT));
        backpack.setItem(6, new ItemStack(Items.COBBLESTONE));
        backpack.setItem(7, new ItemStack(Items.SAND));

        assertFalse(ScavengerCrafting.canGive(backpack, new ItemStack(Items.WOODEN_PICKAXE, 1)));
    }

    /** U-0A: leftover ingredient counts leave no free slot and no merge target. */
    @Test
    void u0a_impossibleFullPackRefusesStoneCraftWithoutChangingInventory() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        backpack.setItem(1, new ItemStack(Items.STICK, 3));
        backpack.setItem(2, new ItemStack(Items.COAL));
        backpack.setItem(3, new ItemStack(Items.TORCH));
        backpack.setItem(4, new ItemStack(Items.DIRT));
        backpack.setItem(5, new ItemStack(Items.SAND));
        backpack.setItem(6, new ItemStack(Items.GRAVEL));
        backpack.setItem(7, new ItemStack(Items.CLAY_BALL));

        assertFalse(ScavengerCrafting.canGive(backpack, new ItemStack(Items.STONE_PICKAXE)));
        assertFalse(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_STONE_PICKAXE));
        assertEquals(4, ScavengerCrafting.count(backpack, Items.COBBLESTONE));
        assertEquals(3, ScavengerCrafting.count(backpack, Items.STICK));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STONE_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.TORCH));
    }

    /** Historical TT-0 torch refusal: no merge room and consuming fuel/stick would free slots —
     * wait, that would succeed under TT-0R. Keep a case where leftover sticks/fuel stay and fillers
     * block: use oversized fuel/stick stacks so slots do not empty. */
    @Test
    void fullBackpackRefusesTorchCraftWhenSlotsStayOccupied() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COAL, 2));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.TORCH, 64));
        backpack.setItem(3, new ItemStack(Items.DIRT));
        backpack.setItem(4, new ItemStack(Items.SAND));
        backpack.setItem(5, new ItemStack(Items.GRAVEL));
        backpack.setItem(6, new ItemStack(Items.COBBLESTONE));
        backpack.setItem(7, new ItemStack(Items.CLAY_BALL));

        assertFalse(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_TORCHES));
        assertEquals(2, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(2, ScavengerCrafting.count(backpack, Items.STICK));
        assertEquals(64, ScavengerCrafting.count(backpack, Items.TORCH));
    }

    @Test
    void torchCraftSucceedsWhenOutputFits() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COAL));
        backpack.setItem(1, new ItemStack(Items.STICK));

        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_TORCHES));
        assertEquals(ScavengerCrafting.TORCHES_PER_CRAFT, ScavengerCrafting.count(backpack, Items.TORCH));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STICK));
    }

    @Test
    void canGiveMatchesGiveForPartialStacks() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.TORCH, 60));

        ItemStack fourTorches = new ItemStack(Items.TORCH, ScavengerCrafting.TORCHES_PER_CRAFT);
        assertTrue(ScavengerCrafting.canGive(backpack, fourTorches));
        assertTrue(ScavengerCrafting.give(backpack, fourTorches));
        assertEquals(64, ScavengerCrafting.count(backpack, Items.TORCH));
    }

    @Test
    void stonePickCraftConsumesCobbleNotPlanks() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));

        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_STONE_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.STONE_PICKAXE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.COBBLESTONE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.OAK_PLANKS));
    }

    @Test
    void stonePickCraftRemovesWoodenPickFromPack() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.WOODEN_PICKAXE));

        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_STONE_PICKAXE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.WOODEN_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.STONE_PICKAXE));
    }

    /**
     * U-0B: ordinary mid-upgrade 8/8 pack — leftover cobble/sticks leave those slots occupied;
     * extracting the wooden pick frees the output slot.
     */
    @Test
    void u0b_fullMidUpgradePackCraftsStonePickAtomically() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG));
        backpack.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
        backpack.setItem(2, new ItemStack(Items.STICK, 4));
        backpack.setItem(3, new ItemStack(Items.COAL));
        backpack.setItem(4, new ItemStack(Items.TORCH, 4));
        backpack.setItem(5, new ItemStack(Items.WOODEN_PICKAXE));
        backpack.setItem(6, new ItemStack(Items.WOODEN_AXE));
        backpack.setItem(7, new ItemStack(Items.COBBLESTONE, 6));

        assertFalse(ScavengerCrafting.canGive(backpack, new ItemStack(Items.STONE_PICKAXE)));
        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_STONE_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.STONE_PICKAXE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.WOODEN_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.WOODEN_AXE));
        assertEquals(3, ScavengerCrafting.count(backpack, Items.COBBLESTONE));
        assertEquals(2, ScavengerCrafting.count(backpack, Items.STICK));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(4, ScavengerCrafting.count(backpack, Items.TORCH));
    }

    /**
     * U-0C: torch stack has only 3 free spaces; consuming coal+stick frees a slot for the 4th torch.
     */
    @Test
    void u0c_torchCraftMergesAfterTransactionFreesASlot() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.COAL));
        backpack.setItem(1, new ItemStack(Items.STICK));
        backpack.setItem(2, new ItemStack(Items.TORCH, 61));
        backpack.setItem(3, new ItemStack(Items.DIRT));
        backpack.setItem(4, new ItemStack(Items.SAND));
        backpack.setItem(5, new ItemStack(Items.GRAVEL));
        backpack.setItem(6, new ItemStack(Items.COBBLESTONE));
        backpack.setItem(7, new ItemStack(Items.CLAY_BALL));

        ItemStack fourTorches = new ItemStack(Items.TORCH, ScavengerCrafting.TORCHES_PER_CRAFT);
        assertFalse(ScavengerCrafting.canGive(backpack, fourTorches));
        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_TORCHES));
        assertEquals(65, ScavengerCrafting.count(backpack, Items.TORCH));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.COAL));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STICK));
    }

    @Test
    void pickUpgradeComesBeforeAxeUpgrade() {
        ScavengerConfig cfg = new ScavengerConfig();
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        backpack.setItem(1, new ItemStack(Items.COBBLESTONE, 6));
        backpack.setItem(2, new ItemStack(Items.STICK, 4));

        assertEquals(ScavengerCrafting.Step.MAKE_STONE_PICKAXE, ScavengerCrafting.nextStep(backpack, cfg));
    }

    @Test
    void tt2b_sharedIronPickSpecDrivesSelectionAndAtomicCraft() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        backpack.setItem(1, new ItemStack(Items.STONE_AXE));
        backpack.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        backpack.setItem(3, new ItemStack(Items.STICK, 2));

        ScavengerCrafting.ConsumerRecipeSpec spec = ScavengerCrafting.activeIronToolRecipe(
                backpack, ItemStack.EMPTY, cfg).orElseThrow();
        assertEquals(ScavengerCrafting.Step.MAKE_IRON_PICKAXE, spec.step());
        assertEquals(3, spec.requiredCount(Items.IRON_INGOT));
        assertEquals(2, spec.requiredCount(Items.STICK));
        assertEquals(ScavengerCrafting.Step.MAKE_IRON_PICKAXE,
                ScavengerCrafting.nextStep(backpack, cfg));

        assertTrue(ScavengerCrafting.apply(backpack, spec.step()));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.IRON_PICKAXE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STONE_PICKAXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.STONE_AXE));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.IRON_INGOT));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STICK));
    }

    @Test
    void tt2b_ironAxeBecomesFrontierOnlyAfterPickIsSatisfied() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        backpack.setItem(1, new ItemStack(Items.STONE_AXE));
        backpack.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        backpack.setItem(3, new ItemStack(Items.STICK, 2));

        assertEquals(ScavengerCrafting.Step.MAKE_IRON_AXE,
                ScavengerCrafting.activeIronToolRecipe(backpack, ItemStack.EMPTY, cfg)
                        .orElseThrow().step());
    }

    @Test
    void tt2b_equippedIronPickSkipsPickAndMainHandStoneAxeIsDisposed() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        ItemStack mainHand = new ItemStack(Items.STONE_AXE);
        ItemStack[] dropped = {ItemStack.EMPTY};

        assertEquals(ScavengerCrafting.Step.MAKE_IRON_AXE,
                ScavengerCrafting.activeIronToolRecipe(backpack, mainHand, cfg).orElseThrow().step());
        assertTrue(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_IRON_AXE,
                mainHand, stack -> dropped[0] = stack));
        assertTrue(mainHand.isEmpty());
        assertTrue(dropped[0].is(Items.STONE_AXE));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.IRON_AXE));
    }

    @Test
    void tt2b_fullPackIronCraftRollsBackWhenNoSlotCanBeFreed() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        backpack.setItem(1, new ItemStack(Items.STICK, 3));
        backpack.setItem(2, new ItemStack(Items.COAL));
        backpack.setItem(3, new ItemStack(Items.TORCH));
        backpack.setItem(4, new ItemStack(Items.DIRT));
        backpack.setItem(5, new ItemStack(Items.SAND));
        backpack.setItem(6, new ItemStack(Items.GRAVEL));
        backpack.setItem(7, new ItemStack(Items.CLAY_BALL));

        assertFalse(ScavengerCrafting.apply(backpack, ScavengerCrafting.Step.MAKE_IRON_PICKAXE));
        assertEquals(4, ScavengerCrafting.count(backpack, Items.IRON_INGOT));
        assertEquals(3, ScavengerCrafting.count(backpack, Items.STICK));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.IRON_PICKAXE));
    }
}
