package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String decision = bodyOf(goal, "private Optional<Candidate> authorizedCandidate(");

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

        String body = bodyOf(gather, "private void publishRouteExhaustion(");
        assertTrue(body.contains("scanScope != null"),
                "a cooperative sub-probe is not a completed bounded search");
        assertTrue(body.contains("NO_CANDIDATES_IN_RADIUS"),
                "protected candidates mean the material IS there");
        assertTrue(body.contains("GatherRoutePrecursor.scanCovers("),
                "the scan must have been asked for this demand's precursor");
        assertTrue(body.contains("Reason.SEARCH_COMPLETED_EMPTY"));
    }

    /** The episode's lifetime is bound to the consumer in exactly one place, not four. */
    @Test
    void mustHappen_theDemandFunnelOwnsTheEvidenceEpisode() {
        assertTrue(bodyOf(source("goal/TradeWithVillagerGoal.java"),
                        "private Optional<WorkDemandPolicy.MaterialDemand> liveDemand(")
                        .contains("RouteExhaustionEvidence.retainOnly("),
                "every path that asks whether a consumer exists funnels through liveDemand");
    }

    // ------------------------------------------------- the whole path, in order

    /**
     * The sequence the User specified, driven through the real policy objects in production order:
     *
     * <pre>
     * completed empty gather scan -> exhaustion evidence -> V2-C authorizes TRADE
     *   -> funding SELL authorized -> SELL executes -> chain continues -> BUY executes
     *   -> consumer closes -> evidence gone
     * </pre>
     *
     * <p>Every step is the production collaborator, and the arithmetic survives a real container
     * across two real transactions. What it cannot show is movement, priority interleaving, or
     * reachability — see this class's header.
     */
    @Test
    void mustHappen_theWholeRouteConnectsFromEmptyScanToClosedConsumer() {
        RouteExhaustionEvidence.shutdownServerState();
        UUID mob = UUID.nameUUIDFromBytes("route-path".getBytes());
        ResourceLocation ironKey = BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT);
        WorkDemandPolicy.MaterialDemand demand =
                new WorkDemandPolicy.MaterialDemand(ironKey, 1, CONSUMER);

        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.STICK, 64));

        // 1. the gather route reports its own completed, empty search
        RouteExhaustionEvidence.publish(
                mob, demand, RouteExhaustionEvidence.Reason.SEARCH_COMPLETED_EMPTY, 0L);

        // 2. UNKNOWN plus that evidence is the only thing that authorizes displacement. Under R3 a
        //    capable pickaxe returned FEASIBLE here and this step was unreachable.
        assertEquals(ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE,
                ExistingRouteFeasibility.reconcile(
                        ExistingRouteFeasibility.ExistingRouteStatus.UNKNOWN,
                        RouteExhaustionEvidence.exhaustedFor(mob, demand, 10L)));

        // 3. the quote this iteration serves, and the shortfall it implies
        OfferSnapshot buyIron = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 2), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
        OfferSnapshot sellSticks = OfferSnapshot.of(1, new MerchantOffer(
                new ItemCost(Items.STICK, 32), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
        List<OfferSnapshot> offers = List.of(buyIron, sellSticks);

        TradeFundingPlanner.FundingTarget funding =
                TradeFundingPlanner.chooseFundingTarget(demand, offers, backpack);
        assertEquals(2, funding.deficit().emeraldsNeeded(), "two emeralds, none held");

        // 4. permission, from the real reserve model - sticks are modelled, and 64 minus a 3-stick
        //    craft claim covers two 32-stick sales
        ScavengerConfig cfg = new ScavengerConfig();
        SellAuthorization authorization = TradeFundingPlanner.authorizeFunding(
                funding.deficit(), offers, backpack, ItemStack.EMPTY, ItemStack.EMPTY,
                m -> SellReserveModel.reservedUnits(m, backpack, cfg));
        assertTrue(authorization.permits(sellSticks.costA()));

        // 5. V2-C admits TRADE
        assertEquals(AcquisitionRoute.TRADE,
                TradeDemandRegistrar.decide(demand,
                                new RouteEvidence(false, offers, true,
                                        funding.deficit(), authorization))
                        .route());

        // 6. V2-D says SELL first, and how many times
        TradeChainPlan plan = TradeChainPlan.forConsumer(CONSUMER, ironKey, 1, 10L);
        TradeChainPolicy.ChainOutcome first = TradeChainPolicy.evaluate(plan,
                new TradeChainPolicy.ChainFacts(true, 0, 0, 2, 1, 61), 10L);
        assertEquals(TradeChainPlan.Step.SELL_TO_FUND, first.plan().step());
        assertEquals(2, first.requiredSellUses());
        assertFalse(first.sellBlocked());

        // 7. two real SELL transactions against a real container
        MerchantOffers live = new MerchantOffers();
        live.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
        live.add(new MerchantOffer(new ItemCost(Items.STICK, 32), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));

        for (int i = 0; i < 2; i++) {
            assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                    VillagerTradeAdapter.executeAgainst(backpack, live, sellSticks, o -> { }));
        }
        assertEquals(2, ScavengerCrafting.count(backpack, Items.EMERALD));
        assertEquals(0, ScavengerCrafting.count(backpack, Items.STICK));

        // 8. actual inventory - never a remembered counter - advances the chain to BUY
        TradeChainPolicy.ChainOutcome funded = TradeChainPolicy.evaluate(first.plan(),
                new TradeChainPolicy.ChainFacts(true, 0,
                        ScavengerCrafting.count(backpack, Items.EMERALD), 2, 1, 0), 20L);
        assertEquals(TradeChainPlan.Step.BUY_TARGET, funded.plan().step());
        assertEquals(0, funded.requiredSellUses(), "no further selling is even representable");

        // 9. the purchase the whole chain existed for
        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(backpack, live, buyIron, o -> { }));
        assertEquals(1, ScavengerCrafting.count(backpack, Items.IRON_INGOT));

        // 10. the consumer closes, and its evidence episode closes with it
        assertEquals(TradeChainPolicy.Termination.CONSUMER_GONE,
                TradeChainPolicy.evaluate(funded.plan(),
                        new TradeChainPolicy.ChainFacts(false, 1, 0, 2, 1, 0), 30L)
                        .termination());

        RouteExhaustionEvidence.retainOnly(mob, null, 30L);
        assertEquals(0, RouteExhaustionEvidence.trackedCount(),
                "the search that authorized this trade cannot authorize the next episode");
        assertFalse(RouteExhaustionEvidence.exhaustedFor(mob, demand, 40L));
    }
}
