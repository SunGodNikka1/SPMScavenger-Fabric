package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy.ChainFacts;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy.ChainOutcome;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy.Termination;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V2-E-R3 — the SELL → BUY chain, end to end across the real seams.
 *
 * <p>Not a unit test of one policy: it walks external demand → chosen BUY quote → deficit →
 * authorization → chain step → transaction → <b>re-derivation from actual inventory</b>. The pieces
 * were each proven in isolation while nothing showed they composed.
 */
class SellToBuyChainTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final UUID GOD = UUID.randomUUID();
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    private static final ResourceLocation OTHER_CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain");

    @BeforeEach
    void clean() {
        RouteExhaustionEvidence.shutdownServerState();
    }

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, CONSUMER);
    }

    /** 9 emeralds → 1 iron ingot. */
    private static OfferSnapshot buyOffer() {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 9), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
    }

    /** 20 wheat → 1 emerald. */
    private static OfferSnapshot sellOffer() {
        return OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
    }

    private static SimpleContainer backpack(int emeralds, int wheat) {
        SimpleContainer container = new SimpleContainer(8);
        if (emeralds > 0) {
            container.setItem(0, new ItemStack(Items.EMERALD, emeralds));
        }
        if (wheat > 0) {
            container.setItem(1, new ItemStack(Items.WHEAT, wheat));
        }
        return container;
    }

    // ---------------------------------------------------------------- the timeline

    /**
     * Need 1 iron · BUY costs 9 emeralds · hold 7 · hold 64 wheat · SELL is 20 wheat → 1 emerald.
     *
     * <p>Every step re-derives from the inventory as it actually is, which is the property that could
     * not be checked while the pieces were only unit-tested.
     */
    @Test
    void mustHappen_theChainSellsExactlyEnoughThenBuys() {
        WorkDemandPolicy.MaterialDemand demand = ironDemand();
        List<OfferSnapshot> offers = List.of(buyOffer(), sellOffer());

        // T0 - the work route publishes a completed, empty search for THIS consumer.
        RouteExhaustionEvidence.publish(
                GOD, demand, RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);
        assertTrue(RouteExhaustionEvidence.exhaustedFor(GOD, demand, 1L));

        SimpleContainer backpack = backpack(7, 64);
        TradeChainPlan chain = TradeChainPlan.forConsumer(
                CONSUMER, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 0L);

        // T1 - the deficit comes from the chosen BUY quote, and the chain says SELL twice.
        TradeFundingPlanner.FundingTarget target =
                TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack);
        assertFalse(target.funded());
        assertEquals(2, target.deficit().emeraldsNeeded(), "9 needed, 7 held");

        SellAuthorization authorization = TradeFundingPlanner.authorizeFunding(
                target.deficit(), offers, backpack, ItemStack.EMPTY, ItemStack.EMPTY, s -> 0);
        assertTrue(authorization.permits(sellOffer().costA()), "wheat is authorized funding stock");

        ChainOutcome t1 = TradeChainPolicy.evaluate(chain, facts(backpack, 9, 1), 1L);
        assertEquals(TradeChainPlan.Step.SELL_TO_FUND, t1.plan().step());
        assertEquals(2, t1.requiredSellUses());

        // T2 - one real SELL: wheat leaves, an emerald arrives.
        sell(backpack);
        assertEquals(8, count(backpack, Items.EMERALD));
        assertEquals(44, count(backpack, Items.WHEAT));

        // T3 - re-derived from actual inventory, not from the earlier number.
        assertEquals(1, TradeChainPolicy.evaluate(chain, facts(backpack, 9, 1), 3L)
                .requiredSellUses());

        // T4 - SPM eats some wheat; enough remains.
        backpack.setItem(1, new ItemStack(Items.WHEAT, 24));
        assertEquals(1, TradeChainPolicy.evaluate(chain, facts(backpack, 9, 1), 4L)
                .requiredSellUses());

        // T5 - second SELL funds the purchase exactly.
        sell(backpack);
        assertEquals(9, count(backpack, Items.EMERALD));

        // T6 - the chain flips to BUY, and asks for no further selling.
        ChainOutcome t6 = TradeChainPolicy.evaluate(chain, facts(backpack, 9, 1), 6L);
        assertEquals(TradeChainPlan.Step.BUY_TARGET, t6.plan().step());
        assertEquals(0, t6.requiredSellUses());

        // T7 - the purchase itself.
        assertTrue(TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack).funded());
        buy(backpack);
        assertEquals(1, count(backpack, Items.IRON_INGOT));
        assertEquals(0, count(backpack, Items.EMERALD));

        // T8/T9 - consumer satisfied, chain terminates, and no further selling is ever requested.
        ChainOutcome t8 = TradeChainPolicy.evaluate(chain, facts(backpack, 9, 1), 8L);
        assertEquals(Termination.TARGET_OBTAINED_ELSEWHERE, t8.termination());
        assertEquals(0, t8.requiredSellUses());
        assertEquals(4, count(backpack, Items.WHEAT), "40 wheat spent, and not one more");
    }

    // ---------------------------------------------------------------- must-not controls

    /** Evidence is bound to a consumer: the iron search says nothing about the torch chain. */
    @Test
    void mustNotHappen_staleEvidenceAuthorizesADifferentConsumer() {
        RouteExhaustionEvidence.publish(
                GOD, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        WorkDemandPolicy.MaterialDemand torch = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 4, OTHER_CONSUMER);
        assertFalse(RouteExhaustionEvidence.exhaustedFor(GOD, torch, 1L));
    }

    /** …and to a material, so the same consumer asking for something else does not inherit it. */
    @Test
    void mustNotHappen_evidenceCrossesMaterials() {
        RouteExhaustionEvidence.publish(
                GOD, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        WorkDemandPolicy.MaterialDemand sameConsumerOtherMaterial =
                new WorkDemandPolicy.MaterialDemand(
                        BuiltInRegistries.ITEM.getKey(Items.DIAMOND), 1, CONSUMER);
        assertFalse(RouteExhaustionEvidence.exhaustedFor(GOD, sameConsumerOtherMaterial, 1L));
    }

    @Test
    void mustHappen_evidenceExpires() {
        RouteExhaustionEvidence.publish(
                GOD, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        assertTrue(RouteExhaustionEvidence.exhaustedFor(
                GOD, ironDemand(), RouteExhaustionEvidence.EVIDENCE_LIFETIME_TICKS - 1));
        assertFalse(RouteExhaustionEvidence.exhaustedFor(
                GOD, ironDemand(), RouteExhaustionEvidence.EVIDENCE_LIFETIME_TICKS),
                "a remembered failure must not authorize trading forever");
        assertEquals(0, RouteExhaustionEvidence.trackedCount(), "and it is deleted, not merely stale");
    }

    /** Evidence belongs to one mob; another mob's failed search is not ours. */
    @Test
    void mustNotHappen_evidenceLeaksBetweenMobs() {
        RouteExhaustionEvidence.publish(
                GOD, ironDemand(), RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);
        assertFalse(RouteExhaustionEvidence.exhaustedFor(UUID.randomUUID(), ironDemand(), 1L));
    }

    /**
     * Protected or reserved stock is not funding. Permission comes from the same layer that stops a
     * wooden pickaxe becoming furnace fuel.
     */
    @Test
    void mustNotHappen_protectedOrReservedStockFundsAPurchase() {
        EmeraldDeficit deficit = new EmeraldDeficit(CONSUMER, 2);

        // Equipment: never spendable, however much the merchant wants it.
        OfferSnapshot buysPickaxes = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.WOODEN_PICKAXE, 1), Optional.empty(),
                new ItemStack(Items.EMERALD, 5), 0, 12, 0, 0f));
        SimpleContainer withPickaxe = new SimpleContainer(8);
        withPickaxe.setItem(0, new ItemStack(Items.WOODEN_PICKAXE, 3));

        assertTrue(TradeFundingPlanner.authorizeFunding(
                        deficit, List.of(buysPickaxes), withPickaxe,
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> 0).isEmpty(),
                "durability marks an investment, whether the buyer is a furnace or a villager");

        // Reserved: held, spare-looking, and already claimed by a craft chain.
        assertTrue(TradeFundingPlanner.authorizeFunding(
                        deficit, List.of(sellOffer()), backpack(0, 30),
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> 25).isEmpty(),
                "a craft reserve is not spare because a villager will pay for it");
    }

    /** An unauthorized material cannot be sold however profitable the offer looks. */
    @Test
    void mustNotHappen_anUnauthorizedMaterialIsSold() {
        EmeraldDeficit deficit = new EmeraldDeficit(CONSUMER, 2);
        SellAuthorization wheatOnly = new SellAuthorization(
                new ItemStack(Items.WHEAT), 64, CONSUMER);

        OfferSnapshot sellsDiamonds = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.DIAMOND, 1), Optional.empty(),
                new ItemStack(Items.EMERALD, 40), 0, 12, 0, 0f));

        assertFalse(TradeEvaluationPolicy.evaluateSell(deficit, wheatOnly, sellsDiamonds).viable(),
                "40 emeralds for one diamond is attractive and still unauthorized");
        assertTrue(TradeEvaluationPolicy.evaluateSell(deficit, wheatOnly, sellOffer()).viable());
    }

    /**
     * The R2 bridge defect: the external purchase demand was passed where an authorization belonged,
     * so funding an iron purchase required selling iron.
     */
    @Test
    void mustHappen_wheatCanFundAnIronPurchase() {
        EmeraldDeficit deficit = new EmeraldDeficit(CONSUMER, 2);
        SellAuthorization wheat = new SellAuthorization(new ItemStack(Items.WHEAT), 64, CONSUMER);

        TradeEvaluationPolicy.Result result =
                TradeEvaluationPolicy.evaluateSell(deficit, wheat, sellOffer());

        assertTrue(result.viable(), "the consumer wants iron; wheat is merely how it is funded");
        assertEquals(CONSUMER, result.evaluation().orElseThrow().consumerKey(),
                "and the leg is attributed to the purchase it exists for");
    }

    /** Consumer disappearing after the first SELL ends the chain rather than funding on. */
    @Test
    void mustNotHappen_theChainKeepsSellingAfterTheConsumerGoes() {
        TradeChainPlan chain = TradeChainPlan.forConsumer(
                CONSUMER, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 0L);
        SimpleContainer backpack = backpack(8, 44);

        ChainOutcome outcome = TradeChainPolicy.evaluate(
                chain, new ChainFacts(false, 0, count(backpack, Items.EMERALD), 9, 1, 2), 5L);

        assertEquals(Termination.CONSUMER_GONE, outcome.termination());
        assertEquals(0, outcome.requiredSellUses());
    }

    /** Sell stock disappearing mid-chain is reported, not fatal, and never lies about the need. */
    @Test
    void mustHappen_vanishedSellStockIsReportedNotFatal() {
        TradeChainPlan chain = TradeChainPlan.forConsumer(
                CONSUMER, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 0L);

        ChainOutcome outcome = TradeChainPolicy.evaluate(
                chain, new ChainFacts(true, 0, 7, 9, 1, 0), 5L);

        assertTrue(outcome.active());
        assertTrue(outcome.sellBlocked());
        assertEquals(2, outcome.requiredSellUses(), "the need is unchanged by our inability to meet it");
    }

    /** A stale BUY price must not size a later sell count. */
    @Test
    void mustNotHappen_aStaleBuyPriceSizesTheSellCount() {
        WorkDemandPolicy.MaterialDemand demand = ironDemand();
        SimpleContainer backpack = backpack(7, 64);

        OfferSnapshot dearer = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 20), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));

        assertEquals(2, TradeFundingPlanner.chooseFundingTarget(
                demand, List.of(buyOffer(), sellOffer()), backpack).deficit().emeraldsNeeded());

        // Cheapest TOTAL is not cheapest PER UNIT. A 12-emerald offer yielding 4 ingots is the
        // better quote; sizing the deficit from the 9-emerald single-ingot offer would fund a
        // purchase the ranking would not make.
        OfferSnapshot bulk = OfferSnapshot.of(2, new MerchantOffer(
                new ItemCost(Items.EMERALD, 12), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 4), 0, 12, 0, 0f));
        assertEquals(5, TradeFundingPlanner.chooseFundingTarget(
                        demand, List.of(buyOffer(), bulk, sellOffer()), backpack)
                        .deficit().emeraldsNeeded(),
                "12 for the chosen bulk quote minus 7 held - not 9 minus 7 from the cheaper total");
        assertEquals(13, TradeFundingPlanner.chooseFundingTarget(
                        demand, List.of(dearer, sellOffer()), backpack).deficit().emeraldsNeeded(),
                "the deficit tracks the quote actually being served, not a remembered one");
    }

    /** One extra SELL after the purchase is funded is arithmetically impossible. */
    @Test
    void mustNotHappen_anExtraSellAfterFunding() {
        WorkDemandPolicy.MaterialDemand demand = ironDemand();
        TradeFundingPlanner.FundingTarget funded = TradeFundingPlanner.chooseFundingTarget(
                demand, List.of(buyOffer(), sellOffer()), backpack(9, 64));

        assertTrue(funded.funded());
        assertEquals(0, com.noobk.spmscavenger.goal.TradeWithVillagerGoal
                .requiredSellUses(funded.deficit(), sellOffer()));
    }

    // ---------------------------------------------------------------- helpers

    private static ChainFacts facts(SimpleContainer backpack, int purchaseCost, int perSell) {
        return new ChainFacts(
                true,
                count(backpack, Items.IRON_INGOT),
                count(backpack, Items.EMERALD),
                purchaseCost,
                perSell,
                count(backpack, Items.WHEAT) / 20);
    }

    private static int count(SimpleContainer container, net.minecraft.world.item.Item item) {
        return container.countItem(item);
    }

    /** One real SELL through the shipping transaction core. */
    private static void sell(SimpleContainer backpack) {
        net.minecraft.world.item.trading.MerchantOffers offers =
                new net.minecraft.world.item.trading.MerchantOffers();
        MerchantOffer live = new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f);
        offers.add(live);

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(
                        backpack, offers, OfferSnapshot.of(0, live), o -> { }));
    }

    /** One real BUY through the same core. */
    private static void buy(SimpleContainer backpack) {
        net.minecraft.world.item.trading.MerchantOffers offers =
                new net.minecraft.world.item.trading.MerchantOffers();
        MerchantOffer live = new MerchantOffer(
                new ItemCost(Items.EMERALD, 9), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f);
        offers.add(live);

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(
                        backpack, offers, OfferSnapshot.of(0, live), o -> { }));
    }
}
