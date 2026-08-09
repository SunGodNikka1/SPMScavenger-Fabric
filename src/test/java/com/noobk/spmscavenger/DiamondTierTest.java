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
 * Phase 3 — diamond tier. Covers the closed consumer/demand loop and the plausibility gate that
 * keeps the "nothing to gather" resting state alive (D-TTU-024).
 *
 * <p>As in {@code RawIronDemandTest}, the live D-TTU-012 capability gate (iron pick accepted,
 * stone/gold refused for diamond ore) is <b>not</b> asserted here: it resolves through the
 * {@code #minecraft:incorrect_for_*_tool} block tags and {@link Bootstrap#bootStrap()} binds tags
 * empty, so the assertion could pass for the wrong reason. Runtime-verifiable only.
 */
class DiamondTierTest {

    private static final int BACKPACK_SIZE = 8;
    private static final int DEEP = 0;                 // inside the diamond band
    private static final int SURFACE = 64;             // above it

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ScavengerConfig diamondConfig() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.craftTools = true;
        cfg.maxPickTier = ToolTier.DIAMOND;
        cfg.maxAxeTier = ToolTier.DIAMOND;
        return cfg;
    }

    /** An iron pick with diamond reachable is the "upgrade wanted" frontier. */
    private static SimpleContainer ironPickPack() {
        SimpleContainer pack = new SimpleContainer(BACKPACK_SIZE);
        pack.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        return pack;
    }

    // ---- demand loop ----

    @Test
    void mustHappen_diamondWantedDeepWithNoneCarried() {
        assertTrue(WorkDemandPolicy.diamondDeficit(
                ironPickPack(), ItemStack.EMPTY, diamondConfig(), DEEP) > 0);
    }

    @Test
    void mustHappen_carriedDiamondsReduceTheDeficit() {
        SimpleContainer pack = ironPickPack();
        ScavengerConfig cfg = diamondConfig();
        int none = WorkDemandPolicy.diamondDeficit(pack, ItemStack.EMPTY, cfg, DEEP);
        pack.setItem(1, new ItemStack(Items.DIAMOND, 1));
        assertEquals(none - 1, WorkDemandPolicy.diamondDeficit(pack, ItemStack.EMPTY, cfg, DEEP));
    }

    @Test
    void mustNotHappen_demandPersistsOnceEnoughDiamondsAreCarried() {
        SimpleContainer pack = ironPickPack();
        pack.setItem(1, new ItemStack(Items.DIAMOND, 64));
        assertEquals(0, WorkDemandPolicy.diamondDeficit(
                pack, ItemStack.EMPTY, diamondConfig(), DEEP));
    }

    @Test
    void mustNotHappen_demandLatchesAfterAToolIsLooted() {
        SimpleContainer pack = ironPickPack();
        ScavengerConfig cfg = diamondConfig();
        assertTrue(WorkDemandPolicy.diamondDeficit(pack, ItemStack.EMPTY, cfg, DEEP) > 0);
        pack.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        assertEquals(0, WorkDemandPolicy.diamondDeficit(pack, ItemStack.EMPTY, cfg, DEEP),
                "a looted diamond pick must end diamond demand immediately");
    }

    // ---- the plausibility gate (D-TTU-024) ----

    @Test
    void mustNotHappen_surfaceMobCarriesDiamondLocalDeficit() {
        assertEquals(0, WorkDemandPolicy.diamondDeficit(
                        ironPickPack(), ItemStack.EMPTY, diamondConfig(), SURFACE),
                "a surface mob must not scan forever for diamond locally");
    }

    @Test
    void mustHappen_surfaceMobStillHasProgressionDemand() {
        assertTrue(WorkDemandPolicy.diamondProgressionDemand(
                        ironPickPack(), ItemStack.EMPTY, diamondConfig()) > 0,
                "progression demand must survive above the band (D-MIW-031)");
        assertFalse(WorkDemandPolicy.isDiamondLocalGatherEligible(SURFACE));
    }

    @Test
    void mustHappen_theSameMobWantsDiamondOnceItIsDeepEnough() {
        SimpleContainer pack = ironPickPack();
        ScavengerConfig cfg = diamondConfig();
        assertEquals(0, WorkDemandPolicy.diamondDeficit(pack, ItemStack.EMPTY, cfg, SURFACE));
        assertTrue(WorkDemandPolicy.diamondDeficit(
                        pack, ItemStack.EMPTY, cfg,
                        WorkDemandPolicy.DIAMOND_GENERATION_CEILING_Y) > 0,
                "at the generation ceiling diamond becomes locally gatherable");
    }

    // ---- tier reachability + frontier ----

    @Test
    void mustNotHappen_diamondWantedWhileTheTierIsCappedLower() {
        ScavengerConfig ironCapped = diamondConfig();
        ironCapped.maxPickTier = ToolTier.IRON;
        ironCapped.maxAxeTier = ToolTier.IRON;
        assertEquals(0, WorkDemandPolicy.diamondDeficit(
                ironPickPack(), ItemStack.EMPTY, ironCapped, DEEP));
    }

    @Test
    void mustHappen_configCapsReachDiamond() {
        assertTrue(ScavengerConfig.CRAFTABLE_TIER_CAPS.contains(ToolTier.DIAMOND),
                "DIAMOND must be selectable or the consumer can never activate");
    }

    @Test
    void mustHappen_diamondCraftConsumesDiamondsAndReplacesTheIronTool() {
        SimpleContainer pack = ironPickPack();
        pack.setItem(1, new ItemStack(Items.DIAMOND, ScavengerCrafting.DIAMOND_PER_TOOL));
        pack.setItem(2, new ItemStack(Items.STICK, ScavengerCrafting.STICKS_PER_TOOL));

        java.util.List<ItemStack> replaced = new java.util.ArrayList<>();
        assertTrue(ScavengerCrafting.apply(
                        pack, ScavengerCrafting.Step.MAKE_DIAMOND_PICKAXE,
                        ItemStack.EMPTY, replaced::add),
                "with diamonds and sticks in the pack the craft must commit");
        assertEquals(1, ScavengerCrafting.count(pack, Items.DIAMOND_PICKAXE));
        assertEquals(0, ScavengerCrafting.count(pack, Items.DIAMOND), "diamonds must be consumed");
        assertEquals(1, replaced.size(), "the replaced iron pick must be handed to the sink");
        assertEquals(Items.IRON_PICKAXE, replaced.get(0).getItem(),
                "replaced tool is disposed deliberately, not silently deleted");
    }

    @Test
    void mustHappen_pickFrontierComesBeforeAxe() {
        SimpleContainer pack = ironPickPack();
        pack.setItem(1, new ItemStack(Items.IRON_AXE));
        pack.setItem(2, new ItemStack(Items.DIAMOND, ScavengerCrafting.DIAMOND_PER_TOOL));
        pack.setItem(3, new ItemStack(Items.STICK, 8));
        assertEquals(ScavengerCrafting.Step.MAKE_DIAMOND_PICKAXE,
                ScavengerCrafting.nextStep(pack, diamondConfig()),
                "with both tools at iron the pick upgrades first");
    }
}
