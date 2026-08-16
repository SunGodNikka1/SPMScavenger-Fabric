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
 * V2-E-R3 — <b>component composition</b> of the SELL → BUY chain. Renamed honestly in R4.
 *
 * <h2>What this proves</h2>
 *
 * That the policy objects compose when a test calls them in the intended order: external demand →
 * chosen BUY quote → deficit → authorization → chain step → transaction → re-derivation from actual
 * inventory. Each had been proven in isolation while nothing showed they fitted together.
 *
 * <h2>What it does not prove, and previously implied</h2>
 *
 * <b>It is the test that calls them in that order, not the mod.</b> Every seam here is invoked
 * directly by the fixture, so this file passed in full while the production registrar used the
 * legacy evaluator, production passed a fabricated {@code material -> 0} reserve, the funding planner
 * ranked by a rule that contradicted V2-B, and the executor ended the round on the first successful
 * trade. A green "end to end" name on a test that supplies its own sequencing is exactly how those
 * four survived review.
 *
 * <p>Whether <i>production</i> reaches this machinery is asserted structurally in
 * {@code TradeProductionWiringTest}, which is a different question and needs a different test.
 */
class SellToBuyChainTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final UUID GOD = UUID.randomUUID();
    /**
     * Injected reserve model for composition fixtures: every material modelled, nothing reserved.
     *
     * <p>Deliberately <b>not</b> what production uses. {@code SellReserveModel} refuses unmodelled
     * materials outright, and {@code SellReserveModelTest} pins that. This exists so the arithmetic
     * under test can use a legible material like wheat without the reserve model being the thing
     * that decides the outcome.
     */
    private static final java.util.function.Function<ItemStack, java.util.OptionalInt> TEST_RESERVE =
            stack -> java.util.OptionalInt.of(0);

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
                TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE);
        assertFalse(target.funded());
        assertEquals(2, target.deficit().emeraldsNeeded(), "9 needed, 7 held");

        // Injected reserve model: wheat is used as the funding material because the arithmetic is
        // legible, NOT because production authorizes wheat today. SellReserveModel currently returns
        // empty for wheat, and SellReserveModelTest pins that refusal separately. See this class's
        // header on what composition tests do and do not prove.
        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                target.deficit(), offers, target.buyOffer(), backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                s -> java.util.OptionalInt.of(0));
        assertTrue(leg.authorization().permits(sellOffer().costA()), "wheat is authorized funding stock");

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
        assertTrue(TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE).funded());
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
        // R4: deleted on mismatch, not merely ignored. Leaving it resident meant that if the iron
        // demand returned inside the lifetime, the OLD search would authorize the NEW episode - a
        // stale record that a later question could still be answered "yes" by.
        assertEquals(0, RouteExhaustionEvidence.trackedCount(),
                "a different demand episode invalidates the evidence outright");
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
        assertEquals(0, RouteExhaustionEvidence.trackedCount(),
                "same consumer, different material is still a different search");
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
                        deficit, List.of(buysPickaxes), null, withPickaxe,
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> java.util.OptionalInt.of(0)) == null,
                "durability marks an investment, whether the buyer is a furnace or a villager");

        // Reserved: held, spare-looking, and already claimed by a craft chain.
        assertTrue(TradeFundingPlanner.authorizeFunding(
                        deficit, List.of(sellOffer()), null, backpack(0, 30),
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> java.util.OptionalInt.of(25)) == null,
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

        assertEquals(2, TradeFundingPlanner.chooseFundingTarget(demand, List.of(buyOffer(), sellOffer()), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE).deficit().emeraldsNeeded());

        assertEquals(13, TradeFundingPlanner.chooseFundingTarget(demand, List.of(dearer, sellOffer()), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE).deficit().emeraldsNeeded(),
                "the deficit tracks the quote actually being served, not a remembered one");
    }

    /**
     * R4 — <b>this assertion used to encode the drift it was written to prevent.</b>
     *
     * <p>The R3 version asserted a deficit of 5, meaning the 12-emerald bulk quote, for a demand of
     * <b>one</b> ingot. That is the planner's own {@code costA / resultCount} rule, and V2-B does not
     * agree with it: V2-B caps a purchase's contribution at the outstanding demand, so buying four
     * ingots to satisfy a demand for one contributes 1 at a unit cost of 12, losing to 9 → 1 at a
     * unit cost of 9. A green negative control was actively protecting the contradiction.
     *
     * <p>Two ranking definitions is one too many. The planner now calls V2-B, and the split below is
     * V2-B's own answer in both directions.
     */
    @Test
    void mustHappen_bulkWinsOnlyWhenTheDemandIsActuallyBulk() {
        SimpleContainer backpack = backpack(7, 64);
        OfferSnapshot bulk = OfferSnapshot.of(2, new MerchantOffer(
                new ItemCost(Items.EMERALD, 12), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 4), 0, 12, 0, 0f));
        List<OfferSnapshot> offers = List.of(buyOffer(), bulk, sellOffer());

        assertEquals(2, TradeFundingPlanner.chooseFundingTarget(ironDemand(), offers, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE)
                        .deficit().emeraldsNeeded(),
                "one ingot wanted: 12 for four of them contributes 1, at a unit cost of 12");

        WorkDemandPolicy.MaterialDemand wantsFour = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 4, CONSUMER);
        assertEquals(5, TradeFundingPlanner.chooseFundingTarget(wantsFour, offers, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE)
                        .deficit().emeraldsNeeded(),
                "four wanted: the bulk quote finally contributes four, at a unit cost of 3");
    }

    /**
     * The regression the User specified: <b>the planner and V2-B must select the same offer index</b>,
     * not merely produce plausible numbers independently.
     *
     * <p>Asserting the deficit alone cannot catch this — two different quotes can imply the same
     * shortfall. Identity is the property that was broken, so identity is what is asserted.
     */
    @Test
    void mustHappen_theFundingPlannerAndV2BSelectTheSameQuote() {
        SimpleContainer backpack = backpack(7, 64);
        OfferSnapshot bulk = OfferSnapshot.of(2, new MerchantOffer(
                new ItemCost(Items.EMERALD, 12), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 4), 0, 12, 0, 0f));
        List<OfferSnapshot> offers = List.of(buyOffer(), bulk, sellOffer());

        for (int wanted : new int[] {1, 2, 3, 4, 8}) {
            WorkDemandPolicy.MaterialDemand demand = new WorkDemandPolicy.MaterialDemand(
                    BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), wanted, CONSUMER);

            TradeEvaluation bestByV2B = TradeDemandRegistrar
                    .decide(demand, RouteEvidence.of(false, offers, true))
                    .best()
                    .orElseThrow(() -> new AssertionError("no viable offer for " + wanted));

            assertEquals(bestByV2B.offerIndex(),
                    TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE)
                            .buyOffer().index(),
                    "planner and V2-B must agree on WHICH quote is served, for demand " + wanted);
        }
    }

    /** One extra SELL after the purchase is funded is arithmetically impossible. */
    @Test
    void mustNotHappen_anExtraSellAfterFunding() {
        WorkDemandPolicy.MaterialDemand demand = ironDemand();
        TradeFundingPlanner.FundingTarget funded = TradeFundingPlanner.chooseFundingTarget(demand, List.of(buyOffer(), sellOffer()), backpack(9, 64),
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE);

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

    // ------------------------------------------------- fundable BUY quotes (R6)

    private static OfferSnapshot buyWithDiamond() {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 5), Optional.of(new ItemCost(Items.DIAMOND, 1)),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
    }

    private static OfferSnapshot buyPlain(int emeralds) {
        return OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
    }

    /**
     * R6 — <b>a quote the mob cannot finish paying for is not a funding target.</b>
     *
     * <p>R5 skipped offers whose {@code costA} was not emeralds and then sized the deficit from
     * {@code costA} alone, so the cheaper-looking compound quote won and funded five emeralds:
     *
     * <pre>
     * A: 5 emerald + 1 diamond -&gt; iron    mob holds no diamond, so A can never execute
     * B: 6 emerald             -&gt; iron    needs 6, and A's deficit is now zero
     * </pre>
     *
     * Nothing would ever sell for the sixth emerald, and the mob would sit funded and unable to buy.
     */
    @Test
    void mustNotHappen_anUnpayableQuoteIsFunded() {
        SimpleContainer noDiamonds = new SimpleContainer(9);

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buyWithDiamond(), buyPlain(6), sellOffer()), noDiamonds,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE);

        assertEquals(1, target.buyOffer().index(),
                "the compound quote is unpayable, so it is not the quote being served");
        assertEquals(6, target.emeraldsRequired(), "and the deficit sizes the quote that IS served");
    }

    /** Holding the second cost makes the same quote fundable again — this is a check, not a ban. */
    @Test
    void mustHappen_aHeldSecondCostMakesTheQuoteFundable() {
        SimpleContainer withDiamond = new SimpleContainer(9);
        withDiamond.setItem(0, new ItemStack(Items.DIAMOND, 1));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buyWithDiamond(), buyPlain(6), sellOffer()), withDiamond,
                ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE);

        assertEquals(0, target.buyOffer().index(), "cheaper in emeralds, and now payable");
        assertEquals(5, target.emeraldsRequired());
    }

    /** Emeralds in either slot count towards the requirement. */
    @Test
    void mustHappen_bothCostSlotsContributeToTheEmeraldRequirement() {
        OfferSnapshot split = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 2), Optional.of(new ItemCost(Items.EMERALD, 3)),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));

        assertEquals(5, TradeFundingPlanner.chooseFundingTarget(
                        ironDemand(), List.of(split, sellOffer()), new SimpleContainer(9),
                        ItemStack.EMPTY, ItemStack.EMPTY, TEST_RESERVE)
                .emeraldsRequired(), "2 + 3, not 2");
    }
}
