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
        // R8 strengthened this: liveness alone was never the question. The PURCHASE must still
        // exist, which only re-resolving the recorded offer can establish.
        assertTrue(body.contains("revalidate(context.buyer(), context.buyQuote())"),
                "the recorded purchase is re-resolved, not merely the buyer entity checked");
        assertTrue(body.contains("TradeSources.of(context.buySource())"),
                "and by the BUY's own source - step 3 carried it precisely so the boundary would "
                        + "never have to guess which source owns the purchase");
        assertTrue(body.contains("VillagerTradeAdapter.available(context.buyer())"),
                "physical legality stays with the executor; the source answers market truth only");
        assertTrue(body.contains("canAffordNonEmerald("),
                "and its non-emerald payment must still be held - owedToPurchase only covers the "
                        + "material being sold, so a diamond spent elsewhere is invisible to it");
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
        assertEquals(1, leg.offer().rankOrdinal(),
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

        assertEquals(1, leg.offer().rankOrdinal(), "both can fund 2 emeralds; 10 sticks each is cheaper");
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

    // ------------------------------------------------- 5. the purchase must still exist (R8)

    private static String goalSource() throws IOException {
        return Files.readString(Path.of(
                        "src/main/java/com/noobk/spmscavenger/goal/TradeWithVillagerGoal.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String methodOf(String source, String signature) {
        String body = source.substring(source.indexOf(signature));
        return body.substring(0, body.indexOf((char) 10 + "    }"));
    }

    /**
     * The R7 P0: <b>liveness is not existence.</b>
     *
     * <p>{@code available(buyer)} proves the entity is alive, awake and unoccupied. It says nothing
     * about whether the trade justifying this sale is still on the board — and a player emptying that
     * offer during the walk leaves all three of those facts true.
     */
    @Test
    void mustNotHappen_anExhaustedOrRepricedPurchaseStillJustifiesTheSale() {
        OfferSnapshot recorded = buyIron(0, 2);

        assertFalse(offer(0, new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY,
                        new ItemStack(Items.IRON_INGOT, 1), 12, 12).isTradeable(),
                "a spent offer cannot be bought from");
        assertFalse(recorded.matchesLive(new MerchantOffer(
                        new ItemCost(Items.EMERALD, 3), Optional.empty(),
                        new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f)),
                "and a reprice from 2 to 3 is a different purchase than the one planned");
        assertTrue(recorded.matchesLive(new MerchantOffer(
                        new ItemCost(Items.EMERALD, 2), Optional.empty(),
                        new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f)),
                "unchanged is unchanged");
    }

    /**
     * The purchase's unrelated payment vanishing during the walk.
     *
     * <p>{@code owedToPurchase} reserves the BUY's non-emerald cost only when it is <b>the same
     * material being sold</b>. A diamond consumed elsewhere while the mob walks off to sell sticks is
     * invisible to it, which is exactly why the boundary asks separately.
     */
    @Test
    void mustNotHappen_aPurchaseWhosePaymentVanishedIsStillFunded() {
        OfferSnapshot buyWithDiamond = offer(0, new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.IRON_INGOT, 1), 0, 12);

        SimpleContainer stillHasIt = new SimpleContainer(9);
        stillHasIt.setItem(0, new ItemStack(Items.DIAMOND, 1));
        assertTrue(VillagerTradeAdapter.canAffordNonEmerald(stillHasIt, buyWithDiamond));

        SimpleContainer spentIt = new SimpleContainer(9);
        spentIt.setItem(0, new ItemStack(Items.STICK, 64));
        assertFalse(VillagerTradeAdapter.canAffordNonEmerald(spentIt, buyWithDiamond),
                "selling sticks to fund a purchase whose diamond is gone funds nothing");
        assertEquals(0, TradeFundingPlanner.owedToPurchase(
                        buyWithDiamond, new ItemStack(Items.STICK)),
                "and the stick reserve is blind to it - different material, zero owed");
    }

    /** Emeralds are excluded: requiring them would refuse every funding sale by construction. */
    @Test
    void mustHappen_theEmeraldCostIsNotRequiredBeforeFunding() {
        assertTrue(VillagerTradeAdapter.canAffordNonEmerald(new SimpleContainer(9), buyIron(0, 5)),
                "the emeralds are exactly what the chain is selling to obtain");
    }

    /**
     * The index trap: a flattened ranking slot is not a merchant's board position.
     *
     * <p>Discovery builds one flat index space across all villagers for ranking, while each candidate
     * keeps its villager-local index for execution. Carrying the flattened snapshot into revalidation
     * would ask buyer A for global index 7 when its actual offer sits at index 2 — revalidating a
     * different trade, or none at all.
     */
    @Test
    void mustNotHappen_theFlattenedRankingIndexAddressesAMerchantsBoard() throws IOException {
        String decision = methodOf(goalSource(),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");
        String context = decision.substring(decision.indexOf("new TradeAttemptFunding("));
        context = context.substring(0, context.indexOf("));"));

        assertTrue(context.contains("buyCandidate.offer()"),
                "the villager's OWN offer is carried, so its index addresses its own board");
        assertFalse(context.contains("funding.buyOffer()"),
                "the flattened ranking snapshot must never become an addressing key");
    }

    // ------------------------------------------------- 6. geometry after the sale (R8)

    /**
     * The R7 P1, and it is Minecraft physics rather than policy.
     *
     * <pre>
     * buyer A        mob        seller B
     *   -15           0           +15       both inside the 16-block scan
     *
     * walk to B, sell, then re-scan around the mob's NEW position (+15)
     *   A is now 30 blocks away and drops out of cognition
     * </pre>
     *
     * A never moved, never died, and still offers the exact purchase the executor is holding in
     * {@code attemptFunding} the entire time. With two sales required it is worse: the second never
     * happens, because the purchase justifying it disappeared after the first.
     */
    @Test
    void mustHappen_theGeometryThatLosesTheBuyerIsReal() {
        double radius = com.noobk.spmscavenger.goal.TradeWithVillagerGoal.CANDIDATE_RADIUS;

        assertTrue(15.0 <= radius, "both villagers are discoverable from the starting point");
        assertTrue(30.0 > radius,
                "and the buyer is out of range once the mob has walked to the seller, so a "
                        + "re-centred scan alone loses it");
    }

    /** The executor carries it — and only as a hint: every fact about it is revalidated. */
    @Test
    void mustHappen_theCarriedBuyerIsAHintNeverAuthority() throws IOException {
        String goal = goalSource();
        String chain = methodOf(goal, "private void continueChain(");
        String discovery = methodOf(goal,
                "private Optional<AuthorizedAttempt> authorizedCandidate(");

        assertTrue(chain.contains("attemptFunding.buyer()") && chain.contains("carriedBuyer"),
                "the buyer of the leg just completed is captured before teardown");
        assertTrue(discovery.contains("nearby.add(carriedBuyer)"),
                "and re-admitted to the candidate set despite the re-centred radius");
        assertTrue(discovery.contains("VillagerTradeAdapter.available(carriedBuyer)")
                        && discovery.contains("round.available(carriedBuyer.getUUID())"),
                "a dead, occupied or demoted buyer is not carried - evidence, never authority");
        assertTrue(discovery.contains("nearby.stream().noneMatch("),
                "and a buyer already inside the radius is not added twice");
    }

    /** Only the post-SELL replan carries one; a demoted candidate has no purchase to inherit. */
    @Test
    void mustNotHappen_reselectInheritsAPurchaseItNeverMade() throws IOException {
        assertTrue(methodOf(goalSource(), "private void reselect(")
                        .contains("authorizedCandidate(level, null)"),
                "a demoted attempt carries nothing forward - inventing a buyer here would be "
                        + "durable villager identity by the back door");
    }
}
