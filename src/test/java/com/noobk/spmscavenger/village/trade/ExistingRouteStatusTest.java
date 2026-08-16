package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.goal.TradeWithVillagerGoal;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility.ExistingRouteStatus;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * V2-E-R2 — the branches R1's structural test could not see.
 *
 * <p>R1 asserted only that the <i>final fall-through</i> was conservative, which proved nothing about
 * the explicit branches — and the iron branch was the one that was wrong. "The default is safe" is
 * not "every path is safe".
 */
class ExistingRouteStatusTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation IRON_TOOL =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    private static WorkDemandPolicy.MaterialDemand demandFor(Item material, ResourceLocation consumer) {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(material), 3, consumer);
    }

    private static SimpleContainer with(Item... items) {
        SimpleContainer container = new SimpleContainer(8);
        for (int i = 0; i < items.length; i++) {
            container.setItem(i, new ItemStack(items[i]));
        }
        return container;
    }

    private static ExistingRouteStatus statusFor(SimpleContainer backpack, Item material) {
        return ExistingRouteFeasibility.gatherStatus(
                demandFor(material, IRON_TOOL), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, new ScavengerConfig());
    }

    /**
     * The R1 defect, behaviourally.
     *
     * <p>{@code GatherIntentPolicy} adds {@code RAW_IRON} to its gather set on exactly
     * {@code rawIronDeficit > 0}. R1 read the same number as "route infeasible" and so let TRADE
     * displace a live gather route. <b>A missing precursor is what gathering is for.</b>
     */
    @Test
    void mustNotHappen_aMissingPrecursorReadsAsAnInfeasibleRoute() {
        ExistingRouteStatus status = statusFor(with(Items.STONE_PICKAXE), Items.IRON_INGOT);

        assertFalse(status.permitsTradeDisplacement(),
                "no raw iron + a usable pickaxe is a LIVE gather route, not a dead one");
    }

    /**
     * The tool-tier guard cannot fire for iron, and that is worth pinning rather than assuming.
     *
     * <p>{@code activeIronToolRecipe} only produces an iron demand when the pick is already
     * {@code >= STONE}, while the guard triggers below stone. They are mutually exclusive, so an
     * iron demand can never reach an unmineable-precursor verdict.
     *
     * <p><b>The consequence is that INFEASIBLE has no reachable producer today, so TRADE does not
     * fire at all.</b> This test exists so that fact is visible in the suite instead of being
     * discovered at runtime as "the mob never trades".
     */
    @Test
    void mustHappen_theInfeasibleSignalHasNoReachableProducerYet() {
        assertEquals(ExistingRouteStatus.UNKNOWN,
                statusFor(with(Items.WOODEN_PICKAXE), Items.IRON_INGOT),
                "a wooden-pick mob has no iron demand at all, so the guard is never consulted");

        for (Item pick : new Item[] {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE}) {
            assertFalse(statusFor(with(pick), Items.IRON_INGOT).permitsTradeDisplacement(),
                    "no pickaxe tier currently authorizes trade displacement for iron: " + pick);
        }
    }

    @Test
    void mustNotHappen_aBetterPickaxeStillReadsAsInfeasible() {
        for (Item pick : new Item[] {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE}) {
            assertFalse(statusFor(with(pick), Items.IRON_INGOT).permitsTradeDisplacement(),
                    pick + " can mine iron ore, so the gather route is alive");
        }
    }

    /** Charcoal: gathering can still go and fetch logs, so an absent smelt plan proves nothing. */
    @Test
    void mustNotHappen_anAbsentCharcoalPlanReadsAsInfeasible() {
        ResourceLocation torchChain =
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain");
        ExistingRouteStatus status = ExistingRouteFeasibility.gatherStatus(
                demandFor(Items.CHARCOAL, torchChain), new SimpleContainer(8),
                ItemStack.EMPTY, ItemStack.EMPTY, new ScavengerConfig());

        assertEquals(ExistingRouteStatus.UNKNOWN, status);
        assertFalse(status.permitsTradeDisplacement());
    }

    /** A material this producer does not model must never authorize displacement. */
    @Test
    void mustNotHappen_anUnmodelledMaterialAuthorizesTrade() {
        assertFalse(statusFor(with(Items.STONE_PICKAXE), Items.DIAMOND).permitsTradeDisplacement());
        assertFalse(statusFor(new SimpleContainer(8), Items.GOLD_INGOT).permitsTradeDisplacement());
    }

    /** The epistemic rule itself: only proven infeasibility displaces. */
    @Test
    void mustHappen_onlyProvenInfeasibilityPermitsDisplacement() {
        assertTrue(ExistingRouteStatus.INFEASIBLE.permitsTradeDisplacement());
        assertFalse(ExistingRouteStatus.FEASIBLE.permitsTradeDisplacement());
        assertFalse(ExistingRouteStatus.UNKNOWN.permitsTradeDisplacement(),
                "ignorance is not a licence to displace working progression");
        assertEquals(ExistingRouteStatus.UNKNOWN,
                ExistingRouteFeasibility.gatherStatus(null, null, null, null, null));
    }

    // ------------------------------------------------- exhaustion precedence

    /**
     * The property the User required explicitly: **immediate positive evidence dominates old
     * negative evidence**. A completed-and-empty search is a memory; a live gather route is the
     * present, and it wins — otherwise "failed once" becomes temporary trade ownership and V2-C's
     * convergence is lost.
     */
    @Test
    void mustNotHappen_staleExhaustionOutranksALiveRoute() {
        assertEquals(ExistingRouteStatus.FEASIBLE,
                ExistingRouteFeasibility.reconcile(ExistingRouteStatus.FEASIBLE, true),
                "raw iron acquired or a smelt plan appearing must beat a remembered failure");
        assertFalse(ExistingRouteFeasibility
                .reconcile(ExistingRouteStatus.FEASIBLE, true).permitsTradeDisplacement());
    }

    /** Exhaustion only speaks when nothing positive can be said. */
    @Test
    void mustHappen_exhaustionResolvesOnlyTheUnknownCase() {
        assertEquals(ExistingRouteStatus.INFEASIBLE,
                ExistingRouteFeasibility.reconcile(ExistingRouteStatus.UNKNOWN, true));
        assertEquals(ExistingRouteStatus.UNKNOWN,
                ExistingRouteFeasibility.reconcile(ExistingRouteStatus.UNKNOWN, false));
        assertEquals(ExistingRouteStatus.INFEASIBLE,
                ExistingRouteFeasibility.reconcile(ExistingRouteStatus.INFEASIBLE, false));
    }

    // ---------------------------------------------------------------- V2-D wiring

    private static OfferSnapshot sellOffer(int emeraldsPerUse) {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, emeraldsPerUse), 0, 12, 0, 0f));
    }

    /**
     * Task 50's handoff: sell uses derive from the <b>live</b> offer and stop at the bounded deficit.
     * Owning disposable wheat is permission to spend wheat, not a reason to sell all of it.
     */
    @Test
    void mustHappen_requiredSellUsesIsBoundedByTheDeficit() {
        TradeEvaluationPolicy.EmeraldDeficit twoShort = new TradeEvaluationPolicy.EmeraldDeficit(
                IRON_TOOL, 2);

        assertEquals(2, TradeWithVillagerGoal.requiredSellUses(twoShort, sellOffer(1)),
                "deficit 2 at one emerald per sell");
        assertEquals(1, TradeWithVillagerGoal.requiredSellUses(twoShort, sellOffer(2)),
                "a richer offer needs fewer sells");
        assertEquals(3, TradeWithVillagerGoal.requiredSellUses(
                        new TradeEvaluationPolicy.EmeraldDeficit(IRON_TOOL, 5), sellOffer(2)),
                "rounds up - the last sell overshoots rather than under-funding");
    }

    @Test
    void mustNotHappen_sellingHappensWithoutADeficit() {
        assertEquals(0, TradeWithVillagerGoal.requiredSellUses(null, sellOffer(1)),
                "no deficit means no emerald appetite at all");
        assertEquals(0, TradeWithVillagerGoal.requiredSellUses(
                new TradeEvaluationPolicy.EmeraldDeficit(IRON_TOOL, 2), null));
    }
}
