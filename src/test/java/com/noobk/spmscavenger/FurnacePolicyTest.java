package com.noobk.spmscavenger;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FS-1 / D-FSM-003 / D-FSM-006 — furnace demand, reserves, and fuel choice. */
class FurnacePolicyTest {

    private static final int BACKPACK_SIZE = 8;

    private static final FurnacePolicy.RecipeLookup TEST_RECIPES = input -> {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (FurnacePolicy.isLog(input)) {
            return Optional.of(new FurnacePolicy.ResolvedSmeltingRecipe(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "charcoal"),
                    input.copyWithCount(1),
                    new ItemStack(Items.CHARCOAL),
                    FurnacePolicy.VANILLA_SMELT_TICKS));
        }
        if (input.is(Items.RAW_IRON)
                || input.is(Items.IRON_ORE)
                || input.is(Items.DEEPSLATE_IRON_ORE)) {
            return Optional.of(new FurnacePolicy.ResolvedSmeltingRecipe(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"),
                    input.copyWithCount(1),
                    new ItemStack(Items.IRON_INGOT),
                    FurnacePolicy.VANILLA_SMELT_TICKS));
        }
        return Optional.empty();
    };

    /** Burn times aligned with vanilla furnace fuel map for the items these tests use. */
    private static final FurnacePolicy.FuelLookup TEST_FUELS = stack -> {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 1600;
        }
        if (FurnacePolicy.isLog(stack) || FurnacePolicy.isPlank(stack)) {
            return 300;
        }
        return 0;
    };

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** U-F1: no coal/charcoal + surplus log → charcoal demand; coal present suppresses it. */
    @Test
    void uF1_logWithoutTorchFuelDemandsCharcoal() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        backpack.setItem(1, new ItemStack(Items.STICK, 4));
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.campfire = false;
        cfg.craftTools = false;

        assertEquals(FurnacePolicy.SmeltDemand.CHARCOAL, FurnacePolicy.demand(backpack, cfg));
        assertTrue(FurnacePolicy.plan(backpack, cfg, TEST_RECIPES, TEST_FUELS).isPresent());

        backpack.setItem(2, new ItemStack(Items.COAL, 1));
        assertEquals(FurnacePolicy.SmeltDemand.NONE, FurnacePolicy.demand(backpack, cfg));
        assertFalse(FurnacePolicy.needsCharcoal(backpack, cfg));
    }

    /** U-F2/FS-8: a live iron-pick consumer derives 3→1→0 ingot deficits. */
    @Test
    void uF2_rawIronWithFuelCanSmeltIron() {
        SimpleContainer withFuel = new SimpleContainer(BACKPACK_SIZE);
        withFuel.setItem(0, new ItemStack(Items.RAW_IRON, 3));
        withFuel.setItem(1, new ItemStack(Items.COAL, 1));
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.placeTorches = false;
        cfg.maxPickTier = ToolTier.IRON;
        withFuel.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        withFuel.setItem(3, new ItemStack(Items.STICK, 2));

        assertEquals(3, WorkDemandPolicy.select(withFuel, ItemStack.EMPTY, cfg)
                .orElseThrow().payload().derivedDeficit());
        assertEquals(FurnacePolicy.SmeltDemand.IRON,
                FurnacePolicy.demand(withFuel, ItemStack.EMPTY, cfg));
        withFuel.setItem(4, new ItemStack(Items.IRON_INGOT, 2));
        assertEquals(1, WorkDemandPolicy.select(withFuel, ItemStack.EMPTY, cfg)
                .orElseThrow().payload().derivedDeficit());
        withFuel.setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        assertTrue(WorkDemandPolicy.select(withFuel, ItemStack.EMPTY, cfg).isEmpty());
        withFuel.setItem(4, ItemStack.EMPTY);
        Optional<FurnacePolicy.SmeltPlan> plan =
                FurnacePolicy.plan(withFuel, cfg, FurnacePolicy.SmeltDemand.IRON, TEST_RECIPES, TEST_FUELS);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().output().is(Items.IRON_INGOT));
        assertTrue(plan.get().fuelChosen().is(Items.COAL));

        SimpleContainer noFuel = new SimpleContainer(BACKPACK_SIZE);
        noFuel.setItem(0, new ItemStack(Items.RAW_IRON, 3));
        noFuel.setItem(1, new ItemStack(Items.STONE_PICKAXE));
        noFuel.setItem(2, new ItemStack(Items.STICK, 2));
        assertEquals(FurnacePolicy.SmeltDemand.IRON,
                FurnacePolicy.demand(noFuel, ItemStack.EMPTY, cfg));
        assertTrue(FurnacePolicy.plan(noFuel, cfg, FurnacePolicy.SmeltDemand.IRON, TEST_RECIPES, TEST_FUELS)
                .isEmpty());
    }

    /** U-F7: raw input and the removed legacy stock knob cannot create producer-only demand. */
    @Test
    void uF7_rawIronWithoutLiveConsumerDoesNotCreateProducerOnlyDemand() {
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON, 3));
        backpack.setItem(1, new ItemStack(Items.COAL));
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.placeTorches = false;

        assertEquals(FurnacePolicy.SmeltDemand.NONE, FurnacePolicy.demand(backpack, cfg));
        assertTrue(WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg).isEmpty());

        cfg.maxPickTier = ToolTier.IRON;
        // The iron recipe is not yet the live frontier until the stone prerequisite exists.
        assertTrue(WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg).isEmpty());
    }

    @Test
    void fs8_pickFrontierSuppressesAxeAndLootedPickMovesDemandToAxe() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.placeTorches = false;
        cfg.maxPickTier = ToolTier.IRON;
        cfg.maxAxeTier = ToolTier.IRON;
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.RAW_IRON, 6));
        backpack.setItem(1, new ItemStack(Items.COAL));
        backpack.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        backpack.setItem(3, new ItemStack(Items.STONE_AXE));
        backpack.setItem(4, new ItemStack(Items.STICK, 4));

        WorkDemandPolicy.MaterialDemand pick = WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg)
                .orElseThrow().payload();
        assertEquals(ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey(), pick.consumerKey());
        backpack.setItem(5, new ItemStack(Items.IRON_PICKAXE));
        WorkDemandPolicy.MaterialDemand axe = WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg)
                .orElseThrow().payload();
        assertEquals(ScavengerCrafting.IRON_AXE_RECIPE.consumerKey(), axe.consumerKey());

        cfg.maxPickTier = ToolTier.STONE;
        cfg.maxAxeTier = ToolTier.STONE;
        assertTrue(WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg).isEmpty());
    }

    @Test
    void fs8_charcoalWinsDeterministicallyOverIronProgression() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.campfire = false;
        cfg.craftTools = false;
        cfg.maxPickTier = ToolTier.IRON;
        SimpleContainer backpack = new SimpleContainer(BACKPACK_SIZE);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        backpack.setItem(1, new ItemStack(Items.RAW_IRON, 3));
        backpack.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        backpack.setItem(3, new ItemStack(Items.STICK, 2));

        WorkDemandPolicy.WorkDemand selected = WorkDemandPolicy.select(backpack, ItemStack.EMPTY, cfg)
                .orElseThrow();
        assertEquals(WorkDemandPolicy.DemandClass.SURVIVAL, selected.demandClass());
        assertEquals(BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), selected.payload().materialKey());
    }

    /** U-F3: reserved last log for sticks is not burned as fuel. */
    @Test
    void uF3_fuelPickerSkipsReservedLogs() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.campfire = false;
        cfg.craftTools = false;

        SimpleContainer oneLog = new SimpleContainer(BACKPACK_SIZE);
        oneLog.setItem(0, new ItemStack(Items.OAK_LOG, 1));
        // Under torch stock, no sticks/planks → reserve 1 log for the craft chain.
        assertEquals(1, FurnacePolicy.logReserveForCraftChain(oneLog, cfg));
        assertEquals(0, FurnacePolicy.surplusLogs(oneLog, cfg));
        assertEquals(FurnacePolicy.SmeltDemand.NONE, FurnacePolicy.demand(oneLog, cfg));

        SimpleContainer twoLogs = new SimpleContainer(BACKPACK_SIZE);
        twoLogs.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        assertEquals(1, FurnacePolicy.surplusLogs(twoLogs, cfg));
        assertEquals(FurnacePolicy.SmeltDemand.CHARCOAL, FurnacePolicy.demand(twoLogs, cfg));

        // One surplus log as input leaves zero surplus for fuel → plan refuses.
        assertTrue(FurnacePolicy.plan(twoLogs, cfg, TEST_RECIPES, TEST_FUELS).isEmpty());

        // Third log (or planks) supplies fuel without touching the stick reserve.
        SimpleContainer threeLogs = new SimpleContainer(BACKPACK_SIZE);
        threeLogs.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        Optional<FurnacePolicy.SmeltPlan> plan =
                FurnacePolicy.plan(threeLogs, cfg, TEST_RECIPES, TEST_FUELS);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().input().is(Items.OAK_LOG));
        assertTrue(plan.get().fuelChosen().is(Items.OAK_LOG));
        assertTrue(plan.get().fuelBurnTicks() >= plan.get().cookingTicks());

        SimpleContainer logPlusPlanks = new SimpleContainer(BACKPACK_SIZE);
        logPlusPlanks.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        logPlusPlanks.setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
        Optional<FurnacePolicy.SmeltPlan> plankFuel =
                FurnacePolicy.plan(logPlusPlanks, cfg, TEST_RECIPES, TEST_FUELS);
        assertTrue(plankFuel.isPresent());
        assertTrue(plankFuel.get().fuelChosen().is(Items.OAK_PLANKS));
    }

    /** Two surplus logs are not enough to smelt — gather must not yield on demand alone. */
    @Test
    void uF4_twoSurplusLogsDemandCharcoalButNoExecutablePlan() {
        ScavengerConfig cfg = new ScavengerConfig();
        cfg.campfire = false;
        cfg.craftTools = false;

        SimpleContainer twoLogs = new SimpleContainer(BACKPACK_SIZE);
        twoLogs.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        assertEquals(FurnacePolicy.SmeltDemand.CHARCOAL, FurnacePolicy.demand(twoLogs, cfg));
        assertTrue(FurnacePolicy.plan(twoLogs, cfg, TEST_RECIPES, TEST_FUELS).isEmpty());

        SimpleContainer threeLogs = new SimpleContainer(BACKPACK_SIZE);
        threeLogs.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        assertTrue(FurnacePolicy.plan(threeLogs, cfg, TEST_RECIPES, TEST_FUELS).isPresent());
    }
}
