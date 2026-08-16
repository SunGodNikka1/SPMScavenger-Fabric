package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
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
 * V2-B — an offer is scored against a demand, and nothing more is concluded.
 */
class TradeEvaluationPolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation IRON_TOOL_FRONTIER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");
    private static final ResourceLocation MENDING_BOOK =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "mending_book");

    private static ResourceLocation key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static WorkDemandPolicy.MaterialDemand demand(Item material, int deficit) {
        return new WorkDemandPolicy.MaterialDemand(key(material), deficit, IRON_TOOL_FRONTIER);
    }

    private static OfferSnapshot offer(Item costItem, int costCount, Item resultItem, int resultCount) {
        return OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(costItem, costCount), Optional.empty(),
                new ItemStack(resultItem, resultCount), 0, 12, 0, 0f));
    }

    // ---------------------------------------------------------------- BUY

    /** The User's worked example: need 3 iron, offer 4 emeralds → 1 iron. */
    @Test
    void mustHappen_aMatchingOfferReportsContributionPaymentAndConsumer() {
        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(
                demand(Items.IRON_INGOT, 3), offer(Items.EMERALD, 4, Items.IRON_INGOT, 1));

        assertTrue(result.viable());
        TradeEvaluation evaluation = result.evaluation().orElseThrow();
        assertEquals(TradeEvaluation.Direction.BUY, evaluation.direction());
        assertEquals(1, evaluation.quantityContribution(), "contributes 1 of 3");
        assertEquals(4, evaluation.totalPaymentItems(), "payment is 4 emeralds");
        assertEquals(IRON_TOOL_FRONTIER, evaluation.consumerKey(),
                "the consumer is carried through, so V2-C can attribute the route");
        assertEquals(key(Items.IRON_INGOT), evaluation.materialKey());
    }

    /**
     * Contribution is capped at the deficit. Over-contribution is how a bounded need becomes an
     * unbounded appetite — "while I am here, another eight".
     */
    @Test
    void mustNotHappen_contributionExceedsTheDeficit() {
        TradeEvaluation evaluation = TradeEvaluationPolicy.evaluate(
                        demand(Items.IRON_INGOT, 3), offer(Items.EMERALD, 1, Items.IRON_INGOT, 16))
                .evaluation().orElseThrow();
        assertEquals(3, evaluation.quantityContribution(), "capped at the deficit, not 16");
    }

    /**
     * The invariant, applied to acquisition: a valuable offer that does not satisfy the demand is
     * rejected outright. Preference affects choice; preference does not create permission.
     */
    @Test
    void mustNotHappen_aValuableButIrrelevantOfferEvaluates() {
        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(
                demand(Items.IRON_INGOT, 3), offer(Items.EMERALD, 3, Items.DIAMOND_SWORD, 1));

        assertFalse(result.viable());
        assertEquals(TradeEvaluationPolicy.TradeRejection.WRONG_MATERIAL, result.rejection());
    }

    @Test
    void mustNotHappen_anExhaustedOfferEvaluates() {
        OfferSnapshot exhausted = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 4), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 12, 12, 0, 0f));

        assertEquals(TradeEvaluationPolicy.TradeRejection.OUT_OF_STOCK,
                TradeEvaluationPolicy.evaluate(demand(Items.IRON_INGOT, 3), exhausted).rejection());
    }

    /** Cheaper per unit must rank above dearer — the number V2-C will compare. */
    @Test
    void mustHappen_utilityPrefersTheCheaperOfferPerUnit() {
        TradeEvaluation cheap = TradeEvaluationPolicy.evaluate(
                        demand(Items.IRON_INGOT, 4), offer(Items.EMERALD, 2, Items.IRON_INGOT, 2))
                .evaluation().orElseThrow();
        TradeEvaluation dear = TradeEvaluationPolicy.evaluate(
                        demand(Items.IRON_INGOT, 4), offer(Items.EMERALD, 8, Items.IRON_INGOT, 2))
                .evaluation().orElseThrow();

        assertEquals(1f, cheap.unitPaymentCost(), 0.001f);
        assertEquals(4f, dear.unitPaymentCost(), 0.001f);
        assertTrue(cheap.utility() > dear.utility());
    }

    @Test
    void mustHappen_coveringMoreOfTheDeficitRanksHigher() {
        TradeEvaluation whole = TradeEvaluationPolicy.evaluate(
                        demand(Items.IRON_INGOT, 4), offer(Items.EMERALD, 4, Items.IRON_INGOT, 4))
                .evaluation().orElseThrow();
        TradeEvaluation partial = TradeEvaluationPolicy.evaluate(
                        demand(Items.IRON_INGOT, 4), offer(Items.EMERALD, 1, Items.IRON_INGOT, 1))
                .evaluation().orElseThrow();
        assertTrue(whole.utility() > partial.utility());
    }

    // ---------------------------------------------------------------- SELL

    /**
     * The rule that stops an emerald appetite: an offer that pays emeralds is not a discovery that
     * emeralds are desirable.
     */
    @Test
    void mustNotHappen_aSellOfferEvaluatesWithoutANamedConsumer() {
        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(
                demand(Items.WHEAT, 64), offer(Items.WHEAT, 20, Items.EMERALD, 1));

        assertFalse(result.viable());
        assertEquals(TradeEvaluationPolicy.TradeRejection.NO_CONSUMER_FOR_PAYMENT,
                result.rejection(),
                "someone will buy my wheat is not a reason to want emeralds");
    }

    @Test
    void mustHappen_aSellOfferEvaluatesAgainstAnEstablishedEmeraldDeficit() {
        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(
                demand(Items.WHEAT, 64),
                offer(Items.WHEAT, 20, Items.EMERALD, 1),
                new TradeEvaluationPolicy.EmeraldDeficit(MENDING_BOOK, 27));

        TradeEvaluation evaluation = result.evaluation().orElseThrow();
        assertEquals(TradeEvaluation.Direction.SELL, evaluation.direction());
        assertEquals(1, evaluation.quantityContribution(), "one emerald toward 27");
        assertEquals(MENDING_BOOK, evaluation.consumerKey(),
                "attributed to the consumer that needs the emeralds, not the material demand");
        assertEquals(key(Items.EMERALD), evaluation.materialKey());
    }

    /** The mob may only sell what the demand is about; choosing otherwise is not this class's call. */
    @Test
    void mustNotHappen_theMobSellsSomethingTheDemandIsNotAbout() {
        TradeEvaluationPolicy.Result result = TradeEvaluationPolicy.evaluate(
                demand(Items.WHEAT, 64),
                offer(Items.DIAMOND, 1, Items.EMERALD, 8),
                new TradeEvaluationPolicy.EmeraldDeficit(MENDING_BOOK, 27));

        assertFalse(result.viable());
        assertEquals(TradeEvaluationPolicy.TradeRejection.WRONG_MATERIAL, result.rejection());
    }

    // ---------------------------------------------------------------- MAIBS (light)

    /**
     * No executor exists yet, but the feedback loop still matters: evaluating must not create demand,
     * accumulate anything, or drift.
     */
    @Test
    void mustNotHappen_repeatedEvaluationDriftsOrAccumulates() {
        WorkDemandPolicy.MaterialDemand need = demand(Items.IRON_INGOT, 3);
        OfferSnapshot snapshot = offer(Items.EMERALD, 4, Items.IRON_INGOT, 1);

        TradeEvaluation first = TradeEvaluationPolicy.evaluate(need, snapshot)
                .evaluation().orElseThrow();
        for (int i = 0; i < 100; i++) {
            TradeEvaluation again = TradeEvaluationPolicy.evaluate(need, snapshot)
                    .evaluation().orElseThrow();
            assertEquals(first.quantityContribution(), again.quantityContribution());
            assertEquals(first.utility(), again.utility(), 0.0001f);
            assertEquals(first.unitPaymentCost(), again.unitPaymentCost(), 0.0001f);
        }
        assertEquals(3, need.derivedDeficit(), "the demand is untouched by being evaluated");
        assertEquals(1, snapshot.result().getCount(), "the offer snapshot is untouched");
    }

    // ---------------------------------------------------------------- structural boundary

    private static String source(String file) throws IOException {
        String raw = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/trade").resolve(file));
        StringBuilder out = new StringBuilder(raw.length());
        boolean inBlock = false;
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlock = true;
                }
                continue;
            }
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * The boundary that erodes gradually: V2-B scores an offer, V2-C admits the route. A container
     * parameter is the first step toward reserving inventory; a demand-selector call is the first
     * step toward becoming a second selector.
     */
    @Test
    void mustNotHappen_thePolicyReachesBeyondScoring() throws IOException {
        String body = source("TradeEvaluationPolicy.java");
        for (String forbidden : List.of(
                "Container",                    // reachability and reservation are V2-C's
                "WorkDemandPolicy.select",      // must not choose the demand it is given
                "VillagerTradeAdapter",         // must not transact
                "performTrade",
                "notifyTrade",
                "Villager",                     // no entity access at all
                "Level")) {
            assertFalse(body.contains(forbidden),
                    "V2-B must not reference " + forbidden + " - it scores an offer, nothing more");
        }
    }

    /** Pure means deterministic: no clock, no randomness. */
    @Test
    void mustNotHappen_thePolicyConsultsAClockOrRandomness() throws IOException {
        String body = source("TradeEvaluationPolicy.java");
        for (String forbidden : List.of("getGameTime", "System.currentTimeMillis", "Random",
                "random", "nanoTime")) {
            assertFalse(body.contains(forbidden), "V2-B must be deterministic: " + forbidden);
        }
    }
}
