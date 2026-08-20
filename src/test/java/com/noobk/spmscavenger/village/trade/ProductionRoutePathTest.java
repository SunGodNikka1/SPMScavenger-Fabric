package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ScavengerCrafting;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeDemandRegistrar.AcquisitionRoute;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V2-E-R5 — the whole acquisition route, from an empty gather scan to a closed consumer.
 *
 * <h2>What each half of this file is for</h2>
 *
 * The <b>behavioural</b> test walks the real policy objects in production order against a real
 * container and two real transactions. The <b>structural</b> tests pin the seams that are welded to
 * {@code ServerLevel} and {@code Mob} and cannot be exercised in a JVM test at all — chain ownership,
 * chain lifetime, and the four guards on the exhaustion publisher.
 *
 * <p>Neither half proves the mob walks anywhere, that goal priorities interleave as predicted, or
 * that a villager is reachable. Runtime stays {@code UNVERIFIED}; VR-T2 is what settles it.
 */
class ProductionRoutePathTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    /** Comments stripped: a structural assertion prose can satisfy measures the wrong artifact. */
    private static String source(String relative) {
        try {
            String text = Files.readString(
                    Path.of("src/main/java/com/noobk/spmscavenger", relative));
            return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + relative, e);
        }
    }

    private static String bodyOf(String source, String signature) {
        String body = source.substring(source.indexOf(signature));
        return body.substring(0, body.indexOf("\n    }"));
    }

    // ------------------------------------------------- V2-D ownership

    /**
     * R5 answered the User's A-or-B question with <b>A</b>.
     *
     * <p>R4's {@code continueChain} independently reproduced most of V2-D's locked invariants —
     * external consumer identity, actual-inventory advancement, deficit re-derivation, stop when
     * funded. It reproduced all of them <b>except the hard lifetime</b>, which existed nowhere else,
     * so a chain that could never complete would have retried until the demand happened to change.
     * Rather than keep two encodings of one state machine and bolt expiry onto the newer one,
     * production now supplies facts and {@link TradeChainPolicy} decides.
     */
    @Test
    void mustHappen_theChainPolicyOwnsTheProductionChain() {
        String goal = source("goal/TradeWithVillagerGoal.java");
        // Scoped to the LIVE candidate path, not the file. The first version of this test asserted
        // only that the collaborator was named somewhere in the class, and it passed with the whole
        // chain bypassed - advanceChain still existed, it was simply never called. That is the exact
        // defect class this slice keeps repairing, reintroduced in the test that polices it.
        String decision = bodyOf(goal, "private Optional<AuthorizedAttempt> authorizedCandidate(");

        assertTrue(decision.contains("advanceChain("),
                "the candidate path must consult V2-D, not merely define a method that could");
        assertTrue(decision.contains("outcome.active()"),
                "a terminated chain must not yield a candidate");
        assertTrue(decision.contains("outcome.plan().step()"),
                "and the leg must come from the chain's step, not from the ranking's opinion");

        assertTrue(bodyOf(goal, "private TradeChainPolicy.ChainOutcome advanceChain(")
                        .contains("TradeChainPolicy.evaluate("),
                "V2-D decides; a second copy of the same transitions is what Option A rejected");
    }

    /**
     * A lifetime that resets on every interruption is not a lifetime.
     *
     * <p>{@code stop()} runs whenever the goal yields — combat, shelter, a higher-priority goal.
     * Clearing the plan there would restart the 6000-tick clock each time, and the hard expiry that
     * justified Option A would never once fire.
     */
    @Test
    void mustNotHappen_theChainLifetimeResetsOnEveryInterruption() {
        assertFalse(bodyOf(source("goal/TradeWithVillagerGoal.java"), "public void stop() {")
                        .contains("chain = null"),
                "only termination and consumer change end a chain, not yielding for one tick");
    }

    /** And the policy really does expire, so the invariant Option A was chosen for exists. */
    @Test
    void mustHappen_anUnfinishableChainExpires() {
        TradeChainPlan plan = TradeChainPlan.forConsumer(
                CONSUMER, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 0L);
        TradeChainPolicy.ChainFacts stuck =
                new TradeChainPolicy.ChainFacts(true, 0, 0, 64, 1, 0);

        assertTrue(TradeChainPolicy.evaluate(plan, stuck, 100L).active(),
                "still trying, well inside its lifetime");
        assertEquals(TradeChainPolicy.Termination.EXPIRED,
                TradeChainPolicy.evaluate(plan, stuck, TradeChainPlan.DEFAULT_LIFETIME_TICKS)
                        .termination());
    }

    /**
     * R6 — the executor attempts the <b>exact</b> quote whose economics were computed.
     *
     * <p>R5 derived {@code emeraldsPerSellUse} from the first authorized SELL in the list and then
     * let the registrar's ranking choose what to attempt. Nothing tied those together, so V2-D could
     * size the chain against a one-emerald sale while V2-E walked off to a three-emerald one — Task 50
     * warned about precisely this before an executor existed.
     *
     * <p>The runtime consequence is caught a second time at the transaction by
     * {@code SellFundingLeg.covers}, which {@code ExecutionBoundaryTest} exercises behaviourally.
     * This pins the selection side, which no JVM test can reach.
     */
    @Test
    void mustHappen_theAttemptedQuoteIsTheOneThatWasPlanned() {
        String decision = bodyOf(source("goal/TradeWithVillagerGoal.java"),
                "private Optional<AuthorizedAttempt> authorizedCandidate(");

        assertTrue(decision.contains("sellLeg.offer()"),
                "the SELL leg carries the quote; re-deriving it from the ranking is the drift");
        assertTrue(decision.contains("funding.buyOffer()"),
                "and the BUY is the funded quote, not whatever ranks highest among all offers");
        assertTrue(decision.contains("owners.get(planned.rankOrdinal())"),
                "the candidate is looked up BY that quote");
    }

    /**
     * R7 — production and the tested seam must be <b>literally the same code</b>.
     *
     * <p>R6 added {@code forDemand} and {@code factsFrom}, tested them, and described them as "the
     * same factory the goal calls" — while the goal still inlined both expressions. The values were
     * equivalent, so every behavioural control stayed green and the helpers protected nothing. (The
     * proximate cause was mine: a negative-control loop restored a backup taken before the wiring
     * edit, silently reverting it.)
     *
     * <p>Wiring the calls is what makes breaking a helper break production, and that is now verified.
     * This assertion exists only to keep the coupling from being inlined away again — an
     * equivalence-preserving refactor no behavioural test can detect, and the point at which the
     * helpers would quietly stop covering the caller.
     */
    @Test
    void mustHappen_productionCallsTheSeamsItsRegressionsCover() {
        String advance = bodyOf(source("goal/TradeWithVillagerGoal.java"),
                "private TradeChainPolicy.ChainOutcome advanceChain(");

        assertTrue(advance.contains("TradeChainPlan.forDemand("),
                "inlining held + deficit here is what made the threshold regressions decorative");
        assertTrue(advance.contains("TradeChainPolicy.factsFrom("),
                "and inlining the ChainFacts constructor did the same for the units regression");
    }

    // ------------------------------------------------- the production publisher

    /**
     * The producer that finally makes {@code INFEASIBLE} reachable, and its four guards.
     *
     * <p>Each guard corresponds to a specific lie the publisher could tell. From {@code stop()} it
     * would report an <b>interruption</b> as an exhausted route. Under {@code scanScope} it would
     * report a cooperative sub-probe as a full sweep. On
     * {@code CANDIDATES_ALL_REJECTED_PROTECTION} it would report "the ore is there and protected" as
     * "there is no ore". Without {@code scanCovers} it would let a log scan speak for iron.
     */
    @Test
    void mustHappen_theGatherPublisherIsGuardedAndInTheRightPlace() {
        String gather = source("goal/GatherResourcesGoal.java");

        // Scoped to canUse()'s own body: a file-position check would have been satisfied by a call
        // sitting in any later method, which is not the property being claimed.
        assertTrue(bodyOf(gather, "public boolean canUse() {")
                        .contains("publishRouteExhaustion(cfg, now)"),
                "published inside the scan-completion path itself");
        int stop = gather.indexOf("public void stop()");
        assertTrue(stop < 0 || !bodyOf(gather, "public void stop()")
                        .contains("publishRouteExhaustion"),
                "never from stop() - an interruption has not finished looking");

        // The publisher now RETURNS its result - it is the single authority for a handoff, and the
        // scheduler consumes that rather than re-deriving one (V2-DEF-003c-R1).
        String body = bodyOf(gather,
                "private java.util.Optional<MandatoryHandoffPolicy.HandoffPublication> "
                        + "publishRouteExhaustion(");
        assertTrue(body.contains("return java.util.Optional.of(new MandatoryHandoffPolicy"),
                "a handoff exists only where evidence was actually written");
        assertTrue(body.contains("scanScope != null"),
                "a cooperative sub-probe is not a completed bounded search");
        // V2-DEF-003b: the same property, now asked PER RESOURCE via the D-VR-084 factored route.
        // Families are recorded at pass one, before protection runs, so an iron candidate that
        // protection rejected still marks RAW_IRON present and still blocks the publish - the
        // material IS there. What changed is that a saturated wealth LOG winning target selection
        // no longer answers for iron.
        assertTrue(body.contains("lastScanFamilies.contains(route.precursor())"),
                "protected or not, a candidate of THIS resource means the route is not exhausted");
        assertTrue(body.contains("GatherRoutePrecursor.scanCovers("),
                "the scan must have been asked for this demand's precursor");
        assertTrue(body.contains("Reason.SEARCH_COMPLETED_EMPTY"));
        // D-VR-084 / task-52: the canonical route derivation (demand + precursor) is factored and
        // shared with the pending-claim publisher — one reading of which route Gather owns, not
        // two. The precursor question itself lives in ownedMandatoryRoute.
        String owned = bodyOf(gather, "private java.util.Optional<OwnedRoute> ownedMandatoryRoute(");
        assertTrue(owned.contains("GatherRoutePrecursor.of(demand.get())"),
                "and the question is scoped to the demand's own precursor");
    }

    /** The episode's lifetime is bound to the consumer in exactly one place, not four. */
    @Test
    void mustHappen_theDemandFunnelOwnsTheEvidenceEpisode() {
        String goal = source("goal/TradeWithVillagerGoal.java");
        String funnel = bodyOf(goal, "private Optional<WorkDemandPolicy.MaterialDemand> liveDemand(");

        assertTrue(funnel.contains("RouteExhaustionEvidence.retainOnly("),
                "every path that asks whether a consumer exists funnels through liveDemand");
        // R6: the chain gets the same treatment. R5 bound only the EVIDENCE to the live consumer, so
        // a chain outlived its owner - stop() preserves it deliberately, and advanceChain always
        // reported consumerStillWants = true because it only runs once a demand has been found.
        // CONSUMER_GONE was unreachable, and the stale chain resumed when the consumer reappeared.
        assertTrue(funnel.contains("terminateChainIfOwnerless("),
                "and the chain is bound to the same consumer, in the same one place");

        assertTrue(bodyOf(goal, "private void terminateChainIfOwnerless(")
                        .contains("TradeChainPolicy.evaluate("),
                "routed through V2-D - Option A means a second lifecycle rule beside it is the bug");
    }

    // ------------------------------------------------- the whole path, in order

    /**
     * The sequence the User specified, re-derived at every step exactly as production re-derives it.
     *
     * <pre>
     * completed empty gather scan -&gt; exhaustion evidence -&gt; V2-C authorizes TRADE
     *   -&gt; funding SELL authorized -&gt; SELL -&gt; RE-DERIVE -&gt; SELL -&gt; RE-DERIVE
     *   -&gt; chain advances to BUY -&gt; BUY -&gt; chain stays open -&gt; consumer closes -&gt; evidence gone
     * </pre>
     *
     * <h2>What R5's version of this fixture actually proved</h2>
     *
     * It executed two 32-stick sales back to back from a single authorization, and it was green — but
     * production could not have followed that path. After sale one, 32 sticks remain, 3 are reserved,
     * 29 are disposable, and a 32-stick sale is no longer authorized. <b>The fixture proved a route
     * the mod cannot take</b>, because it never re-derived between transactions.
     *
     * <p>So every transaction here is followed by the same re-derivation the executor performs, and
     * the numbers are chosen so two sales are genuinely affordable: 64 sticks, 3 reserved, 30 per
     * sale, {@code floor(61 / 30) == 2}.
     *
     * <p>It also starts from <b>1 ingot already held against a need for 3</b> — the state R5's
     * deficit-valued chain threshold terminated one purchase short.
     */
    @Test
    void mustHappen_theWholeRouteConnectsFromEmptyScanToClosedConsumer() {
        RouteExhaustionEvidence.shutdownServerState();
        UUID mob = UUID.nameUUIDFromBytes("route-path".getBytes());
        ResourceLocation ironKey = BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
        ScavengerConfig cfg = new ScavengerConfig();

        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));
        backpack.setItem(1, new ItemStack(Items.IRON_INGOT, 1));
        WorkDemandPolicy.MaterialDemand demand =
                new WorkDemandPolicy.MaterialDemand(ironKey, 2, CONSUMER);

        // 1. the gather route reports its own completed, empty search
        RouteExhaustionEvidence.publish(
                mob, demand, RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        // 2. UNKNOWN plus that evidence is the only thing that authorizes displacement. Under R3 a
        //    capable pickaxe returned FEASIBLE here and this step was unreachable.
        assertEquals(ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE,
                ExistingRouteFeasibility.reconcile(
                        ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN,
                        RouteExhaustionEvidence.exhaustedFor(mob, demand, 10L)));

        List<OfferSnapshot> offers = List.of(
                OfferSnapshot.of(0, buyIronOffer()), OfferSnapshot.of(1, sellSticksOffer()));
        MerchantOffers live = new MerchantOffers();
        live.add(buyIronOffer());
        live.add(sellSticksOffer());

        // 3. the quote this iteration serves, its shortfall, and the exact SELL leg that closes it
        TradeFundingPlanner.FundingTarget funding = TradeFundingPlanner.chooseFundingTarget(
                demand, offers, backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                m -> SellReserveModel.reservedUnits(m, backpack, cfg));
        assertEquals(2, funding.deficit().emeraldsNeeded(), "two emeralds, none held");
        assertEquals(2, funding.sellLeg().affordableUses(),
                "61 disposable sticks at 30 per sale is two USES, never 61");

        // 4. V2-C admits TRADE on the authorization the real reserve model produced
        assertEquals(AcquisitionRoute.TRADE,
                TradeDemandRegistrar.decide(demand, new RouteEvidence(false, offers, true,
                                funding.deficit(), funding.sellLeg().authorization()))
                        .route());

        // 5. the chain opens on an ABSOLUTE threshold: 1 held + 2 needed
        TradeChainPlan chain = TradeChainPlan.forConsumer(CONSUMER, ironKey,
                ScavengerCrafting.count(backpack, Items.IRON_INGOT) + demand.derivedDeficit(), 10L);
        assertEquals(3, chain.targetHeldQuantity());

        // 6. two SELL legs, each preceded by its own re-derivation
        for (int sale = 1; sale <= 2; sale++) {
            TradeFundingPlanner.FundingTarget now = TradeFundingPlanner.chooseFundingTarget(
                    demand, offers, backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                    m -> SellReserveModel.reservedUnits(m, backpack, cfg));
            assertFalse(now.funded(), "still short before sale " + sale);
            assertTrue(now.sellLeg().usable(), "still authorized before sale " + sale);

            TradeChainPolicy.ChainOutcome outcome = TradeChainPolicy.evaluate(chain,
                    new TradeChainPolicy.ChainFacts(true,
                            ScavengerCrafting.count(backpack, Items.IRON_INGOT),
                            ScavengerCrafting.count(backpack, Items.EMERALD),
                            now.emeraldsRequired(),
                            now.sellLeg().emeraldsPerUse(),
                            now.sellLeg().affordableUses()),
                    10L);
            assertEquals(TradeChainPlan.Step.SELL_TO_FUND, outcome.plan().step());
            assertFalse(outcome.sellBlocked(), "sale " + sale + " is affordable");
            chain = outcome.plan();

            assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                    VillagerTradeAdapter.executeAgainst(
                            backpack, live, now.sellLeg().offer(), offer -> assertNotNull(offer)));
        }
        assertEquals(2, ScavengerCrafting.count(backpack, Items.EMERALD));
        assertEquals(4, ScavengerCrafting.count(backpack, Items.STICK), "64 less two 30-stick sales");

        // 7. actual inventory - never a remembered counter - advances the chain to BUY
        TradeFundingPlanner.FundingTarget funded = TradeFundingPlanner.chooseFundingTarget(
                demand, offers, backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                m -> SellReserveModel.reservedUnits(m, backpack, cfg));
        assertTrue(funded.funded(), "the purchase is paid for");

        TradeChainPolicy.ChainOutcome ready = TradeChainPolicy.evaluate(chain,
                new TradeChainPolicy.ChainFacts(true,
                        ScavengerCrafting.count(backpack, Items.IRON_INGOT),
                        ScavengerCrafting.count(backpack, Items.EMERALD),
                        funded.emeraldsRequired(), 0, 0), 20L);
        assertEquals(TradeChainPlan.Step.BUY_TARGET, ready.plan().step());
        assertEquals(0, ready.requiredSellUses(), "no further selling is even representable");

        // 8. the purchase the chain existed for - and the chain MUST NOT stop here
        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(
                        backpack, live, funded.buyOffer(), offer -> assertNotNull(offer)));
        assertEquals(2, ScavengerCrafting.count(backpack, Items.IRON_INGOT));

        TradeChainPolicy.ChainOutcome afterFirstBuy = TradeChainPolicy.evaluate(ready.plan(),
                new TradeChainPolicy.ChainFacts(true, 2, 0, 2, 1, 2), 30L);
        assertTrue(afterFirstBuy.active(),
                "2 held against a target of 3 is not the target obtained elsewhere - this is the "
                        + "R5 P0, and a deficit-valued threshold would have stopped the chain here");

        // 9. the consumer closes only once the third ingot exists
        backpack.setItem(2, new ItemStack(Items.IRON_INGOT, 1));
        assertEquals(TradeChainPolicy.Termination.TARGET_OBTAINED_ELSEWHERE,
                TradeChainPolicy.evaluate(afterFirstBuy.plan(),
                                new TradeChainPolicy.ChainFacts(true,
                                        ScavengerCrafting.count(backpack, Items.IRON_INGOT),
                                        0, 2, 1, 2), 40L)
                        .termination());

        RouteExhaustionEvidence.retainOnly(mob, null, 40L);
        assertEquals(0, RouteExhaustionEvidence.trackedCount(),
                "the search that authorized this trade cannot authorize the next episode");
        assertFalse(RouteExhaustionEvidence.exhaustedFor(mob, demand, 50L));
    }

    private static MerchantOffer buyIronOffer() {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 2), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f);
    }

    private static MerchantOffer sellSticksOffer() {
        return new MerchantOffer(new ItemCost(Items.STICK, 30), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f);
    }
}

