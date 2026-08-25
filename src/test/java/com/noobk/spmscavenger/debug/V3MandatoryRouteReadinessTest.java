package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V3MandatoryRouteReadinessTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactFixtureFrontierIsProductionDerivedAndReady() {
        SimpleContainer backpack = fixtureBackpack();
        V3MandatoryRouteReadiness.Result result = V3MandatoryRouteReadiness.evaluatePolicy(
                backpack,
                new ItemStack(Items.STONE_PICKAXE),
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                new V3MandatoryRouteReadiness.TargetEvidence(
                        true, true, "fixture iron ore reachable"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.READY, result.verdict());
        assertEquals("minecraft:iron_ingot", result.material());
        assertEquals("spmscavenger:iron_pickaxe_upgrade", result.consumer());
        assertEquals(Optional.of(GatherIntentPolicy.Resource.RAW_IRON), result.precursor());
        assertEquals(ScavengerCrafting.Step.NOTHING, result.readyCraftStep());
        assertTrue(result.scanCovers());
        assertTrue(result.targetEvidence().eligible());
        assertTrue(result.targetEvidence().reachable());
        assertTrue(FurnacePolicy.plan(
                        backpack,
                        new ScavengerConfig(),
                        FurnacePolicy.SmeltDemand.IRON,
                        input -> Optional.of(new FurnacePolicy.ResolvedSmeltingRecipe(
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                        "iron_ingot"),
                                input.copyWithCount(1),
                                new ItemStack(Items.IRON_INGOT),
                                FurnacePolicy.VANILLA_SMELT_TICKS)),
                        stack -> FurnacePolicy.VANILLA_SMELT_TICKS)
                .isEmpty(), "no raw input means Smelt cannot preempt the Gather frontier");
    }

    @Test
    void missingStonePickCannotMasqueradeAsMandatoryRouteReady() {
        V3MandatoryRouteReadiness.Result result = V3MandatoryRouteReadiness.evaluatePolicy(
                fixtureBackpack(),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                new V3MandatoryRouteReadiness.TargetEvidence(
                        true, true, "target alone is not authority"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.INCOMPLETE, result.verdict());
        assertFalse(result.reason().isBlank());
    }

    @Test
    void eligibleButUnreachableOreCannotOpenTheEvidenceWindow() {
        V3MandatoryRouteReadiness.Result result = V3MandatoryRouteReadiness.evaluatePolicy(
                fixtureBackpack(),
                new ItemStack(Items.STONE_PICKAXE),
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                new V3MandatoryRouteReadiness.TargetEvidence(
                        true, false, "no reachable production-style approach"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.INCOMPLETE, result.verdict());
        assertTrue(result.reason().contains("reachable"));
    }

    @Test
    void fixtureAndWitnessCannotManufactureProductionAuthority() throws Exception {
        String fixture = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/V3MandatoryRouteFixture.java"));
        String witness = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/noobk/spmscavenger/debug/V3MandatoryRouteReadiness.java"));
        String combined = fixture + witness;

        assertTrue(fixture.contains("backpack.clearContent()"));
        assertTrue(fixture.contains("Items.STONE_PICKAXE"));
        assertTrue(fixture.contains("Items.DIAMOND_AXE"));
        assertTrue(fixture.contains("Items.STICK, 2"));
        assertTrue(fixture.contains("Items.TORCH, 8"));
        assertFalse(combined.contains("MandatoryOwnershipRegistry.publish("));
        assertFalse(combined.contains("RouteExhaustionEvidence.publish("));
        assertFalse(combined.contains("VillageWorkAdmission.evaluate("));
        assertFalse(combined.contains("GatherResourcesGoal"));
        assertFalse(combined.contains("moveTo("));
        assertFalse(combined.contains("canUse()"));
    }

    private static SimpleContainer fixtureBackpack() {
        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.TORCH, 8));
        backpack.setItem(3, new ItemStack(Items.DIAMOND_AXE));
        return backpack;
    }
}
