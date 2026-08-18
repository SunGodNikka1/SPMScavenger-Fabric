package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.TradeDemandRegistrar.AcquisitionDecision;
import com.noobk.spmscavenger.village.trade.TradeDemandRegistrar.AcquisitionRoute;
import com.noobk.spmscavenger.village.trade.TradeDemandRegistrar.TradeBlockedReason;
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
 * V2-C — deciding that trading is a legitimate way to satisfy a goal that already exists.
 */
class TradeDemandRegistrarTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation IRON_TOOL =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    private static WorkDemandPolicy.MaterialDemand ironDemand(int deficit) {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), deficit, IRON_TOOL);
    }

    private static OfferSnapshot offer(int index, Item cost, int costN, Item result, int resultN) {
        return OfferSnapshot.of(index, new MerchantOffer(
                new ItemCost(cost, costN), Optional.empty(),
                new ItemStack(result, resultN), 0, 12, 0, 0f));
    }

    // ------------------------------------------------------------- the two named controls

    /**
     * User-named control 1. Refusing trade must never suppress progression.
     *
     * <p>The failure this guards is not "trade loses" — it is trade winning, finding it cannot
     * proceed, and leaving the demand `BLOCKED` while a perfectly good smelt route sat available.
     */
    @Test
    void mustNotHappen_refusingTradeSuppressesTheExistingRoute() {
        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                ironDemand(3), RouteEvidence.existingRouteOnly(true));

        assertEquals(AcquisitionRoute.EXISTING_WORK, decision.route(),
                "smelt/craft remains eligible with no useful villagers loaded");
        assertEquals(TradeBlockedReason.EXISTING_ROUTE_AVAILABLE, decision.blockedReason());
        assertFalse(decision.tradeChosen());
        assertTrue(decision.rankedTradeOffers().isEmpty());
    }

    /** User-named control 2. A useful, affordable offer may legitimately win when work cannot. */
    @Test
    void mustHappen_tradeBecomesACandidateWithBoundedEvidenceAndAffordablePayment() {
        WorkDemandPolicy.MaterialDemand need = ironDemand(3);
        RouteEvidence evidence = RouteEvidence.of(
                false, List.of(offer(0, Items.EMERALD, 4, Items.IRON_INGOT, 1)), true);

        AcquisitionDecision decision = TradeDemandRegistrar.decide(need, evidence);

        assertEquals(AcquisitionRoute.TRADE, decision.route());
        assertEquals(1, decision.best().orElseThrow().quantityContribution());
        assertEquals(IRON_TOOL, decision.best().orElseThrow().consumerKey());

        assertEquals(3, need.derivedDeficit(), "deciding must not have created or changed demand");
    }

    // ------------------------------------------------------------- gates

    /** Gate 3: attractiveness never displaces a feasible existing route. */
    @Test
    void mustNotHappen_anAttractiveOfferBeatsAFeasibleExistingRoute() {
        RouteEvidence spectacular = RouteEvidence.of(
                true, List.of(offer(0, Items.EMERALD, 1, Items.IRON_INGOT, 64)), true);

        AcquisitionDecision decision = TradeDemandRegistrar.decide(ironDemand(3), spectacular);

        assertEquals(AcquisitionRoute.EXISTING_WORK, decision.route(),
                "64 iron for one emerald is still not a reason to abandon a working route");
    }

    /** Gate 4: a candidate needs current evidence, not the memory that offers once existed. */
    @Test
    void mustNotHappen_aCandidateExistsWithoutBoundedOfferEvidence() {
        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                ironDemand(3), RouteEvidence.of(false, List.of(), true));

        assertEquals(AcquisitionRoute.EXISTING_WORK, decision.route());
        assertEquals(TradeBlockedReason.NO_OFFER_EVIDENCE, decision.blockedReason());
    }

    @Test
    void mustHappen_unaffordablePaymentIsNamedAsSuch() {
        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                ironDemand(3),
                RouteEvidence.of(false, List.of(offer(0, Items.EMERALD, 4, Items.IRON_INGOT, 1)), false));

        assertEquals(AcquisitionRoute.EXISTING_WORK, decision.route());
        assertEquals(TradeBlockedReason.PAYMENT_UNAVAILABLE, decision.blockedReason(),
                "distinguishable from having no offer at all - V2-E will want to know which");
    }

    @Test
    void mustHappen_irrelevantOffersLeaveNoViableCandidate() {
        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                ironDemand(3),
                RouteEvidence.of(false,
                        List.of(offer(0, Items.EMERALD, 3, Items.DIAMOND_SWORD, 1)), true));

        assertEquals(TradeBlockedReason.NO_VIABLE_OFFER, decision.blockedReason());
    }

    /** Gate 2: no emerald appetite is invented for a SELL leg. */
    @Test
    void mustNotHappen_aSellLegIsChosenWithoutAnExternalEmeraldDeficit() {
        WorkDemandPolicy.MaterialDemand wheat = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.WHEAT), 64, IRON_TOOL);

        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                wheat,
                RouteEvidence.of(false, List.of(offer(0, Items.WHEAT, 20, Items.EMERALD, 1)), true));

        assertEquals(AcquisitionRoute.EXISTING_WORK, decision.route());
        assertEquals(TradeBlockedReason.NO_VIABLE_OFFER, decision.blockedReason());
    }

    /** Gate 8: several offers may be ranked — and ranking is all that happens. */
    @Test
    void mustHappen_multipleOffersAreRankedBestFirst() {
        AcquisitionDecision decision = TradeDemandRegistrar.decide(
                ironDemand(4),
                RouteEvidence.of(false, List.of(
                        offer(0, Items.EMERALD, 8, Items.IRON_INGOT, 2),
                        offer(1, Items.EMERALD, 2, Items.IRON_INGOT, 2),
                        offer(2, Items.EMERALD, 5, Items.IRON_INGOT, 2)), true));

        assertEquals(3, decision.rankedTradeOffers().size());
        assertEquals(1, decision.best().orElseThrow().tieBreakOrdinal(), "cheapest per unit ranks first");
        assertTrue(decision.rankedTradeOffers().get(0).utility()
                >= decision.rankedTradeOffers().get(1).utility());
        assertTrue(decision.rankedTradeOffers().get(1).utility()
                >= decision.rankedTradeOffers().get(2).utility());
    }

    /** Equal utility must not order randomly, or the "best" offer changes between identical ticks. */
    @Test
    void mustHappen_tiesBreakDeterministically() {
        RouteEvidence evidence = RouteEvidence.of(false, List.of(
                offer(2, Items.EMERALD, 4, Items.IRON_INGOT, 1),
                offer(0, Items.EMERALD, 4, Items.IRON_INGOT, 1),
                offer(1, Items.EMERALD, 4, Items.IRON_INGOT, 1)), true);

        for (int i = 0; i < 20; i++) {
            assertEquals(0, TradeDemandRegistrar.decide(ironDemand(3), evidence)
                    .best().orElseThrow().tieBreakOrdinal(), "lowest round ordinal wins a tie, every time");
        }
    }

    // ------------------------------------------------------------- MAIBS (light)

    /**
     * The decision feedback loop over time: offer appears, disappears, villager becomes unavailable,
     * payment stock changes, the competing route becomes feasible again.
     *
     * <p>Ownership must <b>converge</b>, not oscillate. Because the policy is stateless and TRADE
     * requires the existing route to be infeasible — rather than merely scoring lower — two
     * nearly-equal options cannot flip ownership tick to tick, and returning to earlier evidence
     * returns the earlier decision.
     */
    @Test
    void mustNotHappen_ownershipOscillatesAcrossAChangingWorld() {
        WorkDemandPolicy.MaterialDemand need = ironDemand(3);
        OfferSnapshot useful = offer(0, Items.EMERALD, 4, Items.IRON_INGOT, 1);

        RouteEvidence workAvailable = RouteEvidence.existingRouteOnly(true);
        RouteEvidence workGoneOfferAppears = RouteEvidence.of(false, List.of(useful), true);
        RouteEvidence offerDisappears = RouteEvidence.of(false, List.of(), true);
        RouteEvidence paymentSpent = RouteEvidence.of(false, List.of(useful), false);

        List<RouteEvidence> timeline = List.of(
                workAvailable, workGoneOfferAppears, offerDisappears, paymentSpent,
                workGoneOfferAppears, workAvailable, workGoneOfferAppears);
        List<AcquisitionRoute> observed = timeline.stream()
                .map(e -> TradeDemandRegistrar.decide(need, e).route())
                .toList();

        assertEquals(List.of(
                        AcquisitionRoute.EXISTING_WORK,
                        AcquisitionRoute.TRADE,
                        AcquisitionRoute.EXISTING_WORK,
                        AcquisitionRoute.EXISTING_WORK,
                        AcquisitionRoute.TRADE,
                        AcquisitionRoute.EXISTING_WORK,
                        AcquisitionRoute.TRADE),
                observed,
                "each decision tracks present evidence exactly");

        // Convergence: identical evidence, repeated, never drifts.
        for (RouteEvidence evidence : timeline) {
            AcquisitionRoute first = TradeDemandRegistrar.decide(need, evidence).route();
            for (int i = 0; i < 50; i++) {
                assertEquals(first, TradeDemandRegistrar.decide(need, evidence).route());
            }
        }
        assertEquals(3, need.derivedDeficit(), "the whole timeline created no demand");
    }

    // ------------------------------------------------------------- structural

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

    /** Gates 1, 5, 8, 10 — and statelessness, which is what makes gate 9 unrepresentable. */
    @Test
    void mustNotHappen_theRegistrarReachesBeyondDeciding() throws IOException {
        String body = source("TradeDemandRegistrar.java");
        for (String forbidden : List.of(
                "Container", "Level", "Villager", "getOffers",
                "VillagerTradeAdapter", "performTrade", "notifyTrade",
                "WorkDemandPolicy.select",
                "getGameTime", "System.currentTimeMillis", "Random",
                "private static final Map", "private final")) {
            assertFalse(body.contains(forbidden),
                    "V2-C must not reference " + forbidden + " - it decides a route, nothing more");
        }
    }

    /**
     * Gate 6, structurally: raw V2-B utility may order offers <b>within</b> TRADE, but must never be
     * read while choosing <b>between</b> routes. The route decision is complete before the sort.
     */
    @Test
    void mustNotHappen_utilityIsUsedToChooseBetweenRoutes() throws IOException {
        String body = source("TradeDemandRegistrar.java");

        int lastRouteRefusal = body.lastIndexOf("return existingWork(");
        int firstUtilityRead = body.indexOf("TradeEvaluation::utility");

        assertTrue(firstUtilityRead > 0, "utility is used to rank within TRADE");
        assertTrue(firstUtilityRead > lastRouteRefusal,
                "every route refusal is decided before any utility is read - trade utility and "
                        + "smelt utility do not share units and must never be compared");
        assertEquals(1, body.split("utility", -1).length - 1,
                "exactly one utility reference: the within-TRADE sort");
    }
}
