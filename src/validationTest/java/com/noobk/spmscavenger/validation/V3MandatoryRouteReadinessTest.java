package com.noobk.spmscavenger.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.FurnacePolicy;
import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.activity.MandatoryOwnershipClaim;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V3MandatoryRouteReadinessTest {

    private static final UUID MOB_ID = UUID.fromString("d1ab6ef2-3331-4c5b-a7b2-b31ae14b7259");
    private static final ResourceLocation EXPECTED_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");

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
    void matchingLiveClaimSupersedesFalseGeometry() {
        V3MandatoryRouteReadiness.Result result = evaluateWithClaim(
                matchingClaim(100L, 500L),
                200L,
                new V3MandatoryRouteReadiness.TargetEvidence(
                        false, false, "duplicated geometry rejected fixture"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.READY, result.verdict());
        assertEquals(V3MandatoryRouteReadiness.Source.LIVE_CLAIM, result.source());
        assertEquals(EXPECTED_CONSUMER.toString(), result.claimEvidence().orElseThrow().consumerKey());
        assertEquals(7, result.claimEvidence().orElseThrow().generation());
        assertEquals(100L, result.claimEvidence().orElseThrow().openedAt());
        assertEquals(500L, result.claimEvidence().orElseThrow().expiresAt());
        assertEquals(200L, result.claimEvidence().orElseThrow().currentTick());
        String description = V3MandatoryRouteReadiness.describe(result);
        assertTrue(description.contains("claimConsumerKey=spmscavenger:iron_pickaxe_upgrade"));
        assertTrue(description.contains("claimGeneration=7"));
        assertTrue(description.contains("claimOpenedAt=100"));
        assertTrue(description.contains("claimExpiresAt=500"));
        assertTrue(description.contains("currentTick=200"));
    }

    @Test
    void matchingLiveClaimAlsoWinsWhenGeometryIsReady() {
        V3MandatoryRouteReadiness.Result result = evaluateWithClaim(
                matchingClaim(100L, 500L),
                200L,
                new V3MandatoryRouteReadiness.TargetEvidence(true, true, "reachable"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.READY, result.verdict());
        assertEquals(V3MandatoryRouteReadiness.Source.LIVE_CLAIM, result.source());
    }

    @Test
    void wrongConsumerClaimCannotSatisfyTheGate() {
        MandatoryOwnershipClaim wrong = new MandatoryOwnershipClaim(
                MOB_ID,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "diamond_pickaxe_upgrade"),
                "diagnostic-route-only",
                7,
                100L,
                500L);
        V3MandatoryRouteReadiness.Result result = evaluateWithClaim(
                wrong,
                200L,
                new V3MandatoryRouteReadiness.TargetEvidence(false, false, "unreachable"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.INCOMPLETE, result.verdict());
        assertFalse(result.source() == V3MandatoryRouteReadiness.Source.LIVE_CLAIM);
    }

    @Test
    void matchingClaimCannotOverrideCurrentPolicyDrift() {
        V3MandatoryRouteReadiness.Result result = V3MandatoryRouteReadiness.evaluatePolicy(
                fixtureBackpack(),
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                new V3MandatoryRouteReadiness.TargetEvidence(false, false, "not evaluated"),
                Optional.of(matchingClaim(100L, 500L)),
                200L);

        assertEquals(V3MandatoryRouteReadiness.Verdict.INCOMPLETE, result.verdict());
        assertEquals(V3MandatoryRouteReadiness.Source.NONE, result.source());
    }

    @Test
    void expiredClaimUsesOrdinaryPassiveFallback() {
        V3MandatoryRouteReadiness.Result result = evaluateWithClaim(
                matchingClaim(100L, 200L),
                200L,
                new V3MandatoryRouteReadiness.TargetEvidence(true, true, "reachable fallback"));

        assertEquals(V3MandatoryRouteReadiness.Verdict.READY, result.verdict());
        assertEquals(V3MandatoryRouteReadiness.Source.PASSIVE_FALLBACK, result.source());
    }

    @Test
    void absentClaimUsesOrdinaryPassiveFallback() {
        V3MandatoryRouteReadiness.Result result = V3MandatoryRouteReadiness.evaluatePolicy(
                fixtureBackpack(),
                new ItemStack(Items.STONE_PICKAXE),
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                new V3MandatoryRouteReadiness.TargetEvidence(true, true, "reachable fallback"),
                Optional.empty(),
                200L);

        assertEquals(V3MandatoryRouteReadiness.Verdict.READY, result.verdict());
        assertEquals(V3MandatoryRouteReadiness.Source.PASSIVE_FALLBACK, result.source());
    }

    @Test
    void fixtureAndWitnessCannotManufactureProductionAuthority() throws Exception {
        String fixture = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V3MandatoryRouteFixture.java"));
        String witness = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/validation/java/com/noobk/spmscavenger/validation/V3MandatoryRouteReadiness.java"));
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
        assertFalse(witness.contains("routeIdentity() instanceof"));
        assertTrue(witness.indexOf("if (matchingLiveClaim(claim, now))")
                        < witness.indexOf("TargetEvidence target = inspectFixtureTargets"),
                "a matching live claim must short-circuit before the duplicate geometry oracle");
    }

    private static SimpleContainer fixtureBackpack() {
        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        backpack.setItem(1, new ItemStack(Items.STICK, 2));
        backpack.setItem(2, new ItemStack(Items.TORCH, 8));
        backpack.setItem(3, new ItemStack(Items.DIAMOND_AXE));
        return backpack;
    }

    private static V3MandatoryRouteReadiness.Result evaluateWithClaim(
            MandatoryOwnershipClaim claim,
            long now,
            V3MandatoryRouteReadiness.TargetEvidence target) {
        return V3MandatoryRouteReadiness.evaluatePolicy(
                fixtureBackpack(),
                new ItemStack(Items.STONE_PICKAXE),
                ItemStack.EMPTY,
                new ScavengerConfig(),
                -60,
                target,
                Optional.of(claim),
                now);
    }

    private static MandatoryOwnershipClaim matchingClaim(long openedAt, long expiresAt) {
        return new MandatoryOwnershipClaim(
                MOB_ID,
                EXPECTED_CONSUMER,
                "diagnostic-route-only",
                7,
                openedAt,
                expiresAt);
    }
}
