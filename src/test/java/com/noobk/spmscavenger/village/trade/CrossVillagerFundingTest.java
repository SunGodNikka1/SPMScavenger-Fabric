package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * V2-E-R7 — the four boundaries R6 got structurally right and functionally wrong.
 *
 * <p>Each one shares a shape worth naming: <b>a rule that is correct about the market it can see, and
 * silently wrong about the market that actually exists.</b> R6's execution recheck saw one villager,
 * its leg selection saw list order, its reserve saw craft claims but not the purchase it was funding,
 * and its lifetime saw an empty offer list as a dead chain.
 */
class CrossVillagerFundingTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    /**
     * Resolved lazily, never in a static initialiser.
     *
     * <p>A {@code static final} registry lookup runs at class-init, which JUnit performs <b>before</b>
     * {@code @BeforeAll} — so the field would be evaluated before {@code Bootstrap.bootStrap()} and
     * the whole class fails to initialise. The same ordering trap that made an earlier fixture in
     * this repository order-dependent.
     */
    private static ResourceLocation iron() {
        return BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
    }

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(iron(), 1, CONSUMER);
    }

    private static OfferSnapshot offer(
            int index, ItemStack costA, ItemStack costB, ItemStack result, int uses, int maxUses) {
        return OfferSnapshot.of(index, new MerchantOffer(
                new ItemCost(costA.getItem(), costA.getCount()),
                costB.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ItemCost(costB.getItem(), costB.getCount())),
                result, uses, maxUses, 0, 0f));
    }

    private static OfferSnapshot buyIron(int index, int emeralds) {
        return offer(index, new ItemStack(Items.EMERALD, emeralds), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 1), 0, 12);
    }

    private static OfferSnapshot sellSticks(int index, int sticks, int emeralds, int uses) {
        return offer(index, new ItemStack(Items.STICK, sticks), ItemStack.EMPTY,
                new ItemStack(Items.EMERALD, emeralds), uses, 12);
    }

    private static OptionalInt reserve(ItemStack stack, SimpleContainer backpack) {
        return SellReserveModel.reservedUnits(stack, backpack, new ScavengerConfig());
    }

    // ------------------------------------------------- 1. buyer A is not seller B

    /**
     * The R6 P0, and the reason its own fixture could not see it.
     *
     * <p>The ordinary village arrangement is a toolsmith who sells iron and a fletcher who buys
     * sticks. R6 re-derived the entire funding decision at execution from the offers of {@code
     * target} — the villager being <b>sold to</b> — and {@code chooseFundingTarget} returns null
     * without a BUY quote serving the demand. So the fletcher's own offer list contains no iron, and
     * every cross-villager funding SELL was refused at the moment of execution.
     *
     * <p>The fix is to stop rediscovering the purchase. It is carried as attempt evidence, so
     * reauthorization only has to ask the seller's own offers whether <b>this</b> sale is still
     * permitted.
     */
    @Test
    void mustHappen_aFundingSellReauthorizesWhenTheBuyerIsADifferentVillager() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));

        OfferSnapshot toolsmithSellsIron = buyIron(0, 2);
        OfferSnapshot fletcherBuysSticks = sellSticks(0, 20, 1, 0);

        // Selection sees the whole neighbourhood and finds a valid leg across two villagers.
        TradeFundingPlanner.FundingTarget planned = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(toolsmithSellsIron, fletcherBuysSticks), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));
        assertNotNull(planned.sellLeg(), "the cross-villager leg is valid at selection");
        assertEquals(2, planned.emeraldsRequired());

        // Execution stands at the fletcher and can see ONLY the fletcher's offers.
        List<OfferSnapshot> sellerOffersOnly = List.of(fletcherBuysSticks);

        assertNull(TradeFundingPlanner.chooseFundingTarget(
                        ironDemand(), sellerOffersOnly, backpack,
                        ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack)),
                "rebuilding the market from the seller alone finds no purchase - this is R6's bug");

        // Carrying the purchase instead: the deficit comes from the recorded requirement, and only
        // the sale itself is revalidated against the seller.
        SellFundingLeg reauthorized = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, planned.emeraldsRequired()),
                sellerOffersOnly, toolsmithSellsIron, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));

        assertNotNull(reauthorized, "the sale is still permitted, and that is the question asked");
        assertTrue(reauthorized.covers(fletcherBuysSticks));
    }

    /** And the executor really does carry it rather than rediscovering it. */
    @Test
    void mustHappen_theExecutorCarriesThePurchaseIntoReauthorization() throws IOException {
        String goal = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        String body = goal.substring(goal.indexOf("private boolean stillAuthorized("));
        body = body.substring(0, body.indexOf((char) 10 + "    }"));

        assertTrue(body.contains("context.buyQuote()") && body.contains("context.emeraldsRequired()"),
                "the purchase is attempt evidence, carried from selection");
        assertFalse(body.contains("fundingTarget("),
                "rebuilding the whole market from the seller is what broke cross-villager funding");
        assertTrue(body.contains("VillagerTradeAdapter.available(context.buyer())"),
                "the buyer's liveness is checked without inspecting its offers");
    }

    // ------------------------------------------------- 2. leg selection is V2-B's job

    /**
     * List order was a <b>deadlock</b>, not a preference.
     *
     * <p>R6 returned the first authorized offer it encountered. A 30-stick sale that affords one use
     * therefore beat a 10-stick sale that affords four, {@code TradeChainPolicy} reported
     * {@code sellBlocked}, and the candidate path returned nothing at all — the mob refuses to trade
     * while a working funding route sits one element further down the list.
     */
    @Test
    void mustNotHappen_anUnfundableLegWinsBecauseItComesFirst() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 43));

        OfferSnapshot expensiveFirst = sellSticks(0, 30, 1, 0);
        OfferSnapshot cheaperSecond = sellSticks(1, 10, 1, 0);

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 2), List.of(expensiveFirst, cheaperSecond),
                null, backpack, ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));

        assertNotNull(leg);
        assertEquals(1, leg.offer().index(),
                "40 disposable sticks: the 30-stick sale affords one use and cannot fund 2");
        assertTrue(leg.fullyFunds(2), "the chosen leg must be able to finish the job");
    }

    /** With both legs sufficient, V2-B's utility decides — sufficiency is a filter, not the rule. */
    @Test
    void mustHappen_v2bRanksAmongLegsThatCanAllFund() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));

        OfferSnapshot dearer = sellSticks(0, 20, 1, 0);
        OfferSnapshot better = sellSticks(1, 10, 1, 0);

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 2), List.of(dearer, better),
                null, backpack, ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));

        assertEquals(1, leg.offer().index(), "both can fund 2 emeralds; 10 sticks each is cheaper");
    }

    /** A merchant's remaining stock bounds the plan: uses it does not have cannot be sequenced. */
    @Test
    void mustNotHappen_aPlanExceedsTheMerchantsRemainingUses() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));

        SellFundingLeg nearlySpent = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 3), List.of(sellSticks(0, 10, 1, 11)),
                null, backpack, ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));

        assertEquals(1, nearlySpent.affordableUses(),
                "61 disposable sticks would afford six uses; the merchant has one left");
        assertFalse(nearlySpent.fullyFunds(3), "and three emeralds are therefore out of reach");
    }

    // ------------------------------------------------- 3. the purchase's own payment

    /**
     * The funding action must not spend its own future payment.
     *
     * <p>{@code SellReserveModel} protects existing craft-chain claims. The quote being funded is
     * another real claim, and R6 checked it only <i>before</i> funding started:
     *
     * <pre>
     * BUY   5 emerald + 12 sticks -&gt; iron      18 sticks held, 3 craft-reserved
     * SELL  10 sticks -&gt; 5 emerald             authorized: 15 disposable
     * after the sale                            8 sticks left - the BUY needs 12
     * </pre>
     *
     * The mob sells its way out of the purchase it was selling for, and the chain sits funded and
     * unable to execute.
     */
    @Test
    void mustNotHappen_theFundingSellSpendsTheBuysOwnPayment() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 18));

        OfferSnapshot buyWithSticks = offer(0, new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.STICK, 12), new ItemStack(Items.IRON_INGOT, 1), 0, 12);
        OfferSnapshot sellSticks = sellSticks(1, 10, 5, 0);

        assertEquals(12, TradeFundingPlanner.owedToPurchase(
                        buyWithSticks, new ItemStack(Items.STICK)),
                "the purchase still owes twelve sticks");

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 5), List.of(sellSticks), buyWithSticks, backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, s -> reserve(s, backpack));

        assertNull(leg,
                "18 sticks less 3 craft-reserved less 12 owed leaves 3 - not enough for a 10-sale");
    }

    /** With enough sticks for both the craft chain and the purchase, the sale is legal again. */
    @Test
    void mustHappen_fundingProceedsWhenBothClaimsAreCovered() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 40));

        OfferSnapshot buyWithSticks = offer(0, new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.STICK, 12), new ItemStack(Items.IRON_INGOT, 1), 0, 12);

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 5), List.of(sellSticks(1, 10, 5, 0)),
                buyWithSticks, backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                s -> reserve(s, backpack));

        assertNotNull(leg, "40 less 3 less 12 leaves 25 spare");
        assertEquals(25, leg.authorization().disposableUnits());
    }

    /** An emerald cost is not "owed": emeralds are what the chain exists to acquire. */
    @Test
    void mustNotHappen_theEmeraldCostIsCountedAsAMaterialReserve() {
        OfferSnapshot plain = buyIron(0, 5);
        assertEquals(0, TradeFundingPlanner.owedToPurchase(plain, new ItemStack(Items.EMERALD)));
        assertEquals(0, TradeFundingPlanner.owedToPurchase(null, new ItemStack(Items.STICK)));
    }

    // ------------------------------------------------- 4. the hard lifetime

    /**
     * A villager strolling out of range must not restart the clock.
     *
     * <p>R6 did {@code if (funding == null) chain = null;} <i>outside</i> the policy, so any tick
     * without a usable quote destroyed the plan and the next quote built a fresh one with a fresh
     * expiry. Repeat and the hard lifetime never arrives — the same defect the review rejected for
     * combat interruption, keyed on market visibility instead. And the hard lifetime is the single
     * invariant Option A was chosen to preserve.
     */
    @Test
    void mustNotHappen_aVanishingQuoteRestartsTheHardLifetime() {
        TradeChainPlan opened = TradeChainPlan.forDemand(CONSUMER, iron(), 0, 1, 0L);

        TradeChainPolicy.ChainOutcome idle =
                TradeChainPolicy.withoutMarketEvidence(opened, 0, 3_000L);

        assertTrue(idle.active(), "the consumer still wants iron; only the evidence is missing");
        assertSame(opened, idle.plan(), "the same plan, not a replacement");
        assertEquals(0L, idle.plan().createdAtTick());
        assertEquals(TradeChainPlan.DEFAULT_LIFETIME_TICKS, idle.plan().expiresAtTick(),
                "the original clock survives the market blackout");
    }

    /** The clock still runs out while the market is absent — this is preservation, not immortality. */
    @Test
    void mustHappen_anIdleChainStillExpires() {
        TradeChainPlan opened = TradeChainPlan.forDemand(CONSUMER, iron(), 0, 1, 0L);

        assertEquals(TradeChainPolicy.Termination.EXPIRED,
                TradeChainPolicy.withoutMarketEvidence(
                                opened, 0, TradeChainPlan.DEFAULT_LIFETIME_TICKS)
                        .termination());
    }

    /** And acquiring the target elsewhere still closes it, market or no market. */
    @Test
    void mustHappen_anIdleChainClosesWhenTheTargetArrives() {
        TradeChainPlan opened = TradeChainPlan.forDemand(CONSUMER, iron(), 1, 2, 0L);

        assertTrue(TradeChainPolicy.withoutMarketEvidence(opened, 2, 100L).active(),
                "2 of 3 is not done");
        assertEquals(TradeChainPolicy.Termination.TARGET_OBTAINED_ELSEWHERE,
                TradeChainPolicy.withoutMarketEvidence(opened, 3, 100L).termination());
    }

    /** The executor must route the empty-market case through the policy, not null the field. */
    @Test
    void mustHappen_theExecutorPreservesTheChainAcrossAnEmptyMarket() throws IOException {
        String goal = Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        String body = goal.substring(goal.indexOf("private TradeChainPolicy.ChainOutcome advanceChain("));
        body = body.substring(0, body.indexOf((char) 10 + "    }"));

        assertTrue(body.contains("TradeChainPolicy.withoutMarketEvidence("),
                "an absent quote is evaluated by V2-D, which owns termination");
    }
}
