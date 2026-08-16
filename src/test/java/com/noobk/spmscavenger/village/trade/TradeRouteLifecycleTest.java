package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.GatherIntentPolicy;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.TradeRejection;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * V2-E-R5 — the two lifecycle defects R4 left, and the route model the publisher depends on.
 *
 * <p>Both defects share a shape this slice keeps rediscovering: <b>a guard that is correct about the
 * question it was asked, and silent about a question nobody asked it.</b> R4's evidence check
 * answered "is this a different demand?" and never "is there a demand at all?"; the sell
 * authorization answered "may I spend cost A?" and never "what else does this transaction spend?".
 */
class TradeRouteLifecycleTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetRuntimeState() {
        RouteExhaustionEvidence.shutdownServerState();
    }

    private static final UUID MOB = UUID.nameUUIDFromBytes("r5-mob".getBytes());
    private static final ResourceLocation IRON_TOOL =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3, IRON_TOOL);
    }

    // ------------------------------------------------- demand-episode invalidation

    /**
     * The R5 P1, exactly as the User sequenced it.
     *
     * <p>R4 deleted evidence when a <i>different</i> demand was queried. It could not see the
     * ordinary success path, where the same identity disappears and returns: buy the iron, the demand
     * resolves, crafting consumes it, and the same consumer wants iron again inside the 2400-tick
     * lifetime. Nobody queried with a different demand at any point, so nothing ever invalidated a
     * search that had been answered by a purchase.
     */
    @Test
    void mustNotHappen_aResolvedDemandsSearchAuthorizesItsReturn() {
        RouteExhaustionEvidence.publish(
                MOB, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);
        assertTrue(RouteExhaustionEvidence.exhaustedFor(MOB, ironDemand(), 10L));

        // T3 - the purchase satisfied the consumer and there is no demand at all.
        RouteExhaustionEvidence.retainOnly(MOB, null, 20L);
        assertEquals(0, RouteExhaustionEvidence.trackedCount(),
                "no live consumer means the episode that consumer opened is over");

        // T5 - the same consumer wants iron again, well inside the old lifetime.
        assertFalse(RouteExhaustionEvidence.exhaustedFor(MOB, ironDemand(), 40L),
                "a demand satisfied and re-raised is a new question, not an answered one");
    }

    /** A live, matching demand keeps its evidence: this is invalidation, not amnesia. */
    @Test
    void mustHappen_aStandingDemandKeepsItsSearchEvidence() {
        RouteExhaustionEvidence.publish(
                MOB, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        RouteExhaustionEvidence.retainOnly(MOB, ironDemand(), 20L);

        assertTrue(RouteExhaustionEvidence.exhaustedFor(MOB, ironDemand(), 30L),
                "the consumer never went away, so neither did its completed search");
        assertEquals(1, RouteExhaustionEvidence.trackedCount());
    }

    /**
     * The deficit shrinking is not the consumer disappearing.
     *
     * <p>{@code MaterialDemand} carries a live deficit that changes constantly during a chain. If
     * invalidation keyed off the whole record rather than its identity, every partial acquisition
     * would erase the search — and the mob would rescan the same empty radius each time it picked
     * up one ingot.
     */
    @Test
    void mustNotHappen_aShrinkingDeficitCountsAsANewEpisode() {
        RouteExhaustionEvidence.publish(
                MOB, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        WorkDemandPolicy.MaterialDemand nearlyDone = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, IRON_TOOL);
        RouteExhaustionEvidence.retainOnly(MOB, nearlyDone, 20L);

        assertTrue(RouteExhaustionEvidence.exhaustedFor(MOB, nearlyDone, 30L),
                "same consumer, same material, fewer needed - one continuing episode");
    }

    /** A different consumer still invalidates, as R4 established. Both rules, one code path. */
    @Test
    void mustNotHappen_anotherConsumersDemandInheritsTheEvidence() {
        RouteExhaustionEvidence.publish(
                MOB, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        RouteExhaustionEvidence.retainOnly(MOB, new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 2,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain")), 20L);

        assertEquals(0, RouteExhaustionEvidence.trackedCount());
        assertFalse(RouteExhaustionEvidence.exhaustedFor(MOB, ironDemand(), 30L));
    }

    // ------------------------------------------------- two-cost SELL permission

    private static OfferSnapshot compoundSell() {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.STICK, 20), Optional.of(new ItemCost(Items.DIAMOND, 1)),
                new ItemStack(Items.EMERALD, 5), 0, 12, 0, 0f));
    }

    private static OfferSnapshot simpleSell() {
        return OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.STICK, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
    }

    /**
     * The R5 P1 the User named: <b>permission to spend A must not manufacture permission to spend
     * B</b>.
     *
     * <p>{@code SellAuthorization} authorizes one stack and {@code permits} checks {@code costA}
     * alone, while {@code TradeTransaction} debits {@code costA} and {@code costB}. Sticks are
     * modelled, surplus and authorized; the diamond beside them is unmodelled and must never be
     * spendable. Authorizing the offer on the sticks would hand the adapter a diamond nobody
     * examined.
     */
    @Test
    void mustNotHappen_authorizedCostAUnlocksAnUnauthorizedCostB() {
        SellAuthorization sticks = new SellAuthorization(new ItemStack(Items.STICK, 20), 64, IRON_TOOL);

        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluateSell(
                new EmeraldDeficit(IRON_TOOL, 5), sticks, compoundSell());

        assertTrue(result.evaluation().isEmpty(),
                "the sticks are authorized; the diamond is not, and both would be debited");
        assertEquals(TradeRejection.UNSUPPORTED_COMPOUND_COST, result.rejection());
    }

    /** And the planner refuses to authorize from such an offer in the first place. */
    @Test
    void mustNotHappen_aCompoundCostOfferProducesAnAuthorization() {
        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));
        backpack.setItem(1, new ItemStack(Items.DIAMOND, 5));

        assertTrue(TradeFundingPlanner.authorizeFunding(
                        new EmeraldDeficit(IRON_TOOL, 5), List.of(compoundSell()), backpack,
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> OptionalInt.of(0))
                == null, "refused at the source as well as in the evaluator");
    }

    /** The single-cost equivalent still works: this is a scope limit, not a ban on funding SELL. */
    @Test
    void mustHappen_aSingleCostFundingSellIsStillAuthorized() {
        SellAuthorization sticks = new SellAuthorization(new ItemStack(Items.STICK, 20), 64, IRON_TOOL);

        assertTrue(TradeEvaluationPolicy
                .evaluateSell(new EmeraldDeficit(IRON_TOOL, 5), sticks, simpleSell())
                .evaluation().isPresent());
    }

    /** BUY offers keep V2-A's two-cost support — only funding SELL is narrowed. */
    @Test
    void mustHappen_twoCostBuyOffersAreUnaffected() {
        OfferSnapshot compoundBuy = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 9), Optional.of(new ItemCost(Items.STICK, 2)),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));

        assertTrue(TradeEvaluationPolicy.evaluate(ironDemand(), compoundBuy)
                .evaluation().isPresent(), "the compound-cost restriction is SELL-only");
    }

    // ------------------------------------------------- the route model the publisher uses

    /** An ingot is never gathered: its existing route mines the precursor and smelts it. */
    @Test
    void mustHappen_theDemandMapsToThePrecursorItsRouteActuallySeeks() {
        assertEquals(Optional.of(GatherIntentPolicy.Resource.RAW_IRON),
                GatherRoutePrecursor.of(ironDemand()));
        assertEquals(Optional.of(GatherIntentPolicy.Resource.LOGS),
                GatherRoutePrecursor.of(new WorkDemandPolicy.MaterialDemand(
                        BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 2, IRON_TOOL)));
    }

    /**
     * The publisher's central restriction: <b>a scan that never looked cannot report emptiness</b>.
     *
     * <p>A log-only intent finding nothing says nothing about iron, however completely it ran. This
     * is the mutual-exclusion rule stated the other way round — evidence binds to the selected
     * demand's route, not to whatever P3 work happened to be running.
     */
    @Test
    void mustNotHappen_anUnrelatedScanSpeaksForThisDemand() {
        GatherIntentPolicy.GatherIntent logsOnly = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.LOGS), ScavengerCrafting.Step.NOTHING);

        assertFalse(GatherRoutePrecursor.scanCovers(ironDemand(), logsOnly),
                "a scan for logs is silent about iron");

        GatherIntentPolicy.GatherIntent seekingIron = new GatherIntentPolicy.GatherIntent(
                EnumSet.of(GatherIntentPolicy.Resource.RAW_IRON), ScavengerCrafting.Step.NOTHING);
        assertTrue(GatherRoutePrecursor.scanCovers(ironDemand(), seekingIron));
    }

    /** No modelled route means nothing may publish for it — ignorance is not evidence of absence. */
    @Test
    void mustNotHappen_anUnmodelledMaterialGetsExhaustionEvidence() {
        WorkDemandPolicy.MaterialDemand diamond = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND), 3, IRON_TOOL);
        GatherIntentPolicy.GatherIntent everything = new GatherIntentPolicy.GatherIntent(
                EnumSet.allOf(GatherIntentPolicy.Resource.class), ScavengerCrafting.Step.NOTHING);

        assertEquals(Optional.empty(), GatherRoutePrecursor.of(diamond));
        assertFalse(GatherRoutePrecursor.scanCovers(diamond, everything),
                "scanning for everything does not create a route model for diamond");
        assertFalse(GatherRoutePrecursor.scanCovers(ironDemand(), null));
    }
}
