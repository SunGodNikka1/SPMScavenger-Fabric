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

class ToolTierPolicyTest {

    private static final int BACKPACK_SIZE = 8;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lootedStonePickSkipsUpgradeAtStoneCap() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertFalse(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
        assertEquals(ToolTier.STONE, ToolTierPolicy.tierOfPick(backpack));
    }

    @Test
    void woodOnlyBackpackNeedsStonePickWhenCapIsStone() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertTrue(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
    }

    @Test
    void woodCapStopsStoneUpgradePressure() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.WOOD;
        cfg.maxAxeTier = ToolTier.WOOD;

        assertFalse(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
    }

    @Test
    void brokenPickCountsAsMissing() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        ItemStack broken = new ItemStack(Items.STONE_PICKAXE);
        broken.setDamageValue(broken.getMaxDamage());
        backpack.setItem(0, broken);
        ScavengerConfig cfg = new ScavengerConfig();

        assertTrue(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
    }

    @Test
    void mainHandPickCountsTowardOwnership() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        ScavengerConfig cfg = new ScavengerConfig();
        ItemStack stonePick = new ItemStack(Items.STONE_PICKAXE);

        assertFalse(ToolTierPolicy.needsPickUpgrade(backpack, stonePick, cfg));
    }

    @Test
    void cobbleTargetStopsWhenStoneToolsAreOwned() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        backpack.setItem(1, new ItemStack(Items.STONE_AXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertFalse(ToolTierPolicy.cobbleBelowTarget(backpack, cfg));
    }

    @Test
    void cobbleTargetAppliesWhileStoneUpgradePending() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertTrue(ToolTierPolicy.cobbleBelowTarget(backpack, cfg));
    }

    /** U-9A: gold must not satisfy iron capability / IRON ranking. */
    @Test
    void u9a_goldenPickDoesNotRankAsIron() {
        assertEquals(ToolTier.WOOD, ToolTierPolicy.tierOfPick(Items.GOLDEN_PICKAXE));
        assertEquals(ToolTier.IRON, ToolTierPolicy.tierOfPick(Items.IRON_PICKAXE));

        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.GOLDEN_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.maxPickTier = ToolTier.IRON;

        assertTrue(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
        assertFalse(ToolTierPolicy.ownsAtLeast(backpack, ToolTier.IRON, ToolTierPolicy.ToolKind.PICK));
    }

    /** U-9B: gold axe at stone craft target still wants a stone upgrade. */
    @Test
    void u9b_goldenAxeStillNeedsStoneUpgradeAtStoneCap() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.GOLDEN_AXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertEquals(ToolTier.WOOD, ToolTierPolicy.tierOfAxe(Items.GOLDEN_AXE));
        assertTrue(ToolTierPolicy.needsAxeUpgrade(backpack, cfg));
        assertTrue(ToolTierPolicy.needsPickUpgrade(backpack, cfg)); // no pick yet either
    }

    @Test
    void goldenPickAtStoneCapStillNeedsStoneUpgrade() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.GOLDEN_PICKAXE));
        ScavengerConfig cfg = new ScavengerConfig();

        assertTrue(ToolTierPolicy.needsPickUpgrade(backpack, cfg));
    }

    /**
     * U-11A: stone pick in main hand + stone axe in backpack → cobble demand stops
     * (ownership spans hand + pack).
     */
    @Test
    void u11a_equippedStonePickPlusBackpackStoneAxeStopsCobbleDemand() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.STONE_AXE));
        ScavengerConfig cfg = new ScavengerConfig();
        ItemStack stonePick = new ItemStack(Items.STONE_PICKAXE);

        assertTrue(ToolTierPolicy.cobbleBelowTarget(backpack, cfg));
        assertFalse(ToolTierPolicy.cobbleBelowTarget(backpack, stonePick, cfg));
    }

    /** U-11B: wooden pick in hand must not suppress a pending stone upgrade. */
    @Test
    void u11b_woodenPickInHandDoesNotStopCobbleDemand() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.WOODEN_AXE));
        ScavengerConfig cfg = new ScavengerConfig();
        ItemStack woodenPick = new ItemStack(Items.WOODEN_PICKAXE);

        assertTrue(ToolTierPolicy.cobbleBelowTarget(backpack, woodenPick, cfg));
    }
}
