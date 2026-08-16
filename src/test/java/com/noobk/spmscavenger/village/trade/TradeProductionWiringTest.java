package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeDemandRegistrar.AcquisitionRoute;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
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

/**
 * V2-E-R4 — <b>does production reach the machinery, or only the tests?</b>
 *
 * <h2>Why this file exists</h2>
 *
 * R3 shipped four working components and a green suite in which none of them was reachable from the
 * mod. {@code SellToBuyChainTest} called every seam in the right order and passed, while production
 * called the legacy evaluator, fabricated its reserve evidence, ranked BUY quotes by a rule that
 * contradicted V2-B, and ended the round on the first successful trade. Every one of those is
 * invisible to a test that supplies its own sequencing.
 *
 * <p>So this file asks a different question, in two ways: <b>behaviourally</b> where the decision is
 * pure (the registrar), and <b>structurally</b> where the decision is welded to {@code ServerLevel}
 * and {@code Villager} and cannot be exercised without a running server.
 *
 * <h2>What the structural half is and is not</h2>
 *
 * Reading source text is a weak proof class (AV-1) and it is used deliberately narrowly: each
 * assertion pins <b>which collaborator a call site names</b>, which is exactly the property that was
 * wrong. It cannot prove the executor behaves correctly at runtime — that is VR-T2's job, and it is
 * still held.
 */
class TradeProductionWiringTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    /**
     * Source with comments stripped.
     *
     * <p>Not a nicety: the first run of this file failed because the goal's own javadoc <i>names</i>
     * the {@code material -> 0} defect it describes fixing. A structural assertion that prose can
     * satisfy or break is measuring the wrong artifact — and in the satisfying direction it would be
     * the silent-no-op class all over again, a green check protecting a comment.
     */
    private static String source(String relative) {
        try {
            String text = Files.readString(
                    Path.of("src/main/java/com/noobk/spmscavenger", relative));
            return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + relative, e);
        }
    }

    private static OfferSnapshot buysWheat() {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
    }

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, CONSUMER);
    }

    // ------------------------------------------------- the registrar, behaviourally

    /**
     * The R3 defect, stated as behaviour rather than as a call site.
     *
     * <p>The registrar's own decision must depend on the authorization. Under the legacy evaluator
     * the demand doubled as the authorization, so an iron demand made every wheat-for-emerald offer
     * {@code WRONG_MATERIAL} and no authorization could change the answer.
     */
    @Test
    void mustHappen_theRegistrarsDecisionDependsOnTheSellAuthorization() {
        EmeraldDeficit deficit = new EmeraldDeficit(CONSUMER, 2);
        List<OfferSnapshot> offers = List.of(buysWheat());

        SellAuthorization wheat = new SellAuthorization(new ItemStack(Items.WHEAT, 20), 64, CONSUMER);
        assertEquals(AcquisitionRoute.TRADE,
                TradeDemandRegistrar.decide(ironDemand(),
                        new RouteEvidence(false, offers, true, deficit, wheat)).route(),
                "an authorized funding SELL is a viable way to serve an iron demand");

        assertEquals(AcquisitionRoute.EXISTING_WORK,
                TradeDemandRegistrar.decide(ironDemand(),
                        new RouteEvidence(false, offers, true, deficit, null)).route(),
                "the same offer, unauthorized, is not tradeable - permission is what changed");
    }

    /** Permission is per material: authorizing sticks does not authorize wheat. */
    @Test
    void mustNotHappen_anAuthorizationForOneMaterialUnlocksAnother() {
        SellAuthorization sticks = new SellAuthorization(new ItemStack(Items.STICK, 32), 64, CONSUMER);

        assertEquals(AcquisitionRoute.EXISTING_WORK,
                TradeDemandRegistrar.decide(ironDemand(),
                        new RouteEvidence(false, List.of(buysWheat()), true,
                                new EmeraldDeficit(CONSUMER, 2), sticks)).route());
    }

    /** And a deficit alone is still not appetite: no deficit, no funding SELL. */
    @Test
    void mustNotHappen_aFundingSellRunsWithoutADeficit() {
        SellAuthorization wheat = new SellAuthorization(new ItemStack(Items.WHEAT, 20), 64, CONSUMER);

        assertEquals(AcquisitionRoute.EXISTING_WORK,
                TradeDemandRegistrar.decide(ironDemand(),
                        new RouteEvidence(false, List.of(buysWheat()), true, null, wheat)).route(),
                "selling for emeralds nobody needs is the emerald-appetite defect");
    }

    // ------------------------------------------------- the executor, structurally

    /** The registrar must not reach the legacy evaluator, whose demand doubled as authorization. */
    @Test
    void mustNotHappen_productionUsesTheLegacyDeficitEvaluator() {
        String registrar = source("village/trade/TradeDemandRegistrar.java");

        assertTrue(registrar.contains("TradeEvaluationPolicy.evaluateSell("),
                "funding SELL must be evaluated against an explicit authorization");
        assertFalse(registrar.contains("evaluate(demand, offer, "),
                "the 3-arg overload makes the demand double as the authorization");
    }

    /**
     * The fabricated-evidence defect, pinned by name.
     *
     * <p>{@code material -> 0} compiled, satisfied the signature, and told the permission layer that
     * every material was entirely spare — the session's recurring shape: correct policy, lying
     * caller, green policy tests.
     */
    @Test
    void mustNotHappen_theGoalFabricatesReserveEvidence() {
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertFalse(goal.contains("material -> 0"),
                "a fabricated zero reserve makes SellExpendabilityPolicy unanimously permissive");
        assertTrue(goal.contains("SellReserveModel.reservedUnits("),
                "reserves must come from the model that reads the actual craft chain");
    }

    /** The authorization must reach the decision, not merely be computable. */
    @Test
    void mustHappen_theGoalPassesAuthorizationIntoTheDecision() {
        String goal = source("goal/TradeWithVillagerGoal.java");

        int computed = goal.indexOf("fundingAuthorization(deficit, offers, backpack)");
        assertTrue(computed > 0, "the authorization must be computed in the candidate path");
        assertTrue(goal.indexOf("existingFeasible, offers, affordable, deficit, authorization")
                        > computed,
                "and then handed to RouteEvidence - computing it and dropping it is R3's defect");
    }

    /**
     * One ranking authority. A second one drifts, and R3's drifted in a case its own test blessed.
     */
    @Test
    void mustHappen_theFundingPlannerDelegatesRankingToV2B() {
        String planner = source("village/trade/TradeFundingPlanner.java");

        assertTrue(planner.contains("TradeEvaluationPolicy.evaluate(demand, offer)"),
                "the planner must rank by V2-B rather than reimplement a ranking");
        assertFalse(planner.contains("best.result().getCount()"),
                "dividing by the full result count is the rule that contradicted V2-B");
    }

    /**
     * A successful trade is a state transition, not a failed candidate.
     *
     * <p>R3 routed {@code TRADED} into clearing the target, which ended the goal; had it instead
     * routed into {@code reselect()}, the farmer who just bought our wheat would have been demoted
     * out of the round — making the one merchant proven to trade with us unreachable, immediately
     * after proving it.
     */
    @Test
    void mustNotHappen_aSuccessfulTradeIsTreatedAsAFailedCandidate() {
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertTrue(goal.contains("case TRADED -> continueChain(level);"),
                "a completed trade advances the chain");
        assertFalse(goal.contains("case TRADED -> reselect("),
                "reselect() demotes the villager and consumes its budget");

        String chain = goal.substring(goal.indexOf("private void continueChain("));
        chain = chain.substring(0, chain.indexOf("\n    }"));
        assertFalse(chain.contains("demoteCurrent"),
                "the seller stays selectable, with a fresh approach budget");
        assertTrue(chain.contains("authorizedCandidate(level)"),
                "the next step is re-derived from actual inventory, never from a remembered count");
    }
}
