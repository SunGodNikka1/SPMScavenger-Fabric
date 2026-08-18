package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * D-VR-077 step 3 — <b>provenance is carried, never inferred.</b>
 *
 * <p>Every production candidate is {@code VANILLA} today, so nothing here can be proved by observing
 * a difference in behaviour: there is no second value to differ. What can be proved is that the fact
 * is <i>stored</i>, stored <i>separately</i> from {@link OfferRef}, carried into attempt state, and
 * invisible to policy — which is the whole of what step 3 claims.
 *
 * <p>Trade Everything is not installed, not imported, and not referenced.
 */
class TradeSourceProvenanceTest {

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    // ------------------------------------------------------------------ the fact exists

    /**
     * The BUY carries its own source, independent of the seller's.
     *
     * <p>Single-valued enum, so this is a shape claim rather than a behavioural one — and the shape
     * is exactly what matters: the sale and the purchase are separately sourced, and the first real
     * divergence is already known (a Trade Everything synthetic SELL funding a vanilla Toolsmith
     * BUY). A single shared field would compile fine today and be wrong on the first mixed chain.
     */
    @Test
    void mustHappen_theCarriedPurchaseHasItsOwnSourceField() {
        List<String> components = componentNames(TradeAttemptFunding.class);

        assertTrue(components.contains("buySource"),
                "the carried BUY must name its own source: " + components);
        assertEquals(TradeSourceKey.class,
                Arrays.stream(TradeAttemptFunding.class.getRecordComponents())
                        .filter(c -> c.getName().equals("buySource")).findFirst().orElseThrow()
                        .getType());
        assertTrue(components.contains("buyer") && components.contains("buyQuote"),
                "and it still carries the buyer and the quote it authorized");
    }

    /** Construction with a source is possible without any villager or world. */
    @Test
    void mustHappen_fundingEvidenceIsConstructibleWithAnExplicitSource() {
        TradeAttemptFunding funding = new TradeAttemptFunding(
                net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade"),
                null, TradeSourceKey.VANILLA, null, 13);

        assertEquals(TradeSourceKey.VANILLA, funding.buySource());
        assertEquals(13, funding.emeraldsRequired());
    }

    // ------------------------------------------------------------------ never inferred

    /**
     * The rejected shortcut, made structurally impossible to write accidentally.
     *
     * <p>{@code offer.ref() instanceof BoardIndex ? VANILLA : TRADE_EVERYTHING} works for exactly
     * two sources and turns {@code OfferRef} back into a source enum — the overload step 2 removed.
     * So the ref-owning types must not know the source type exists, and vice versa.
     */
    @Test
    void mustNotHappen_provenanceIsDerivedFromTheOfferRef() throws IOException {
        for (String refOwner : List.of("village/trade/OfferRef.java",
                "village/trade/OfferSnapshot.java",
                "village/trade/VillagerTradeAdapter.java")) {
            assertFalse(source(refOwner).contains("TradeSourceKey.VANILLA"),
                    refOwner + " resolves refs; it must not assign provenance");
        }
        assertTrue(Arrays.stream(TradeSourceKey.class.getDeclaredMethods())
                        .flatMap(m -> Arrays.stream(m.getParameterTypes()))
                        .noneMatch(OfferRef.class::isAssignableFrom),
                "no method on the source key may take an OfferRef - provenance is not a property "
                        + "of how a source addresses its own offers");
    }

    /** Where an offer came from is not a reason to prefer it. */
    @Test
    void mustNotHappen_policyReadsProvenance() throws IOException {
        for (String policy : List.of("village/trade/TradeFundingPlanner.java",
                "village/trade/TradeEvaluationPolicy.java",
                "village/trade/TradeDemandRegistrar.java",
                "village/trade/TradeChainPolicy.java",
                "village/trade/SellFundingLeg.java")) {
            assertFalse(source(policy).contains("TradeSourceKey"),
                    policy + " must not read provenance - the economics say everything policy is "
                            + "entitled to know");
        }
    }

    // ------------------------------------------------------------------ carried, not dropped

    /**
     * Provenance added at selection and lost when the candidate becomes attempt state would be worse
     * than never adding it: step 4 would then infer the source at the execution boundary, which is
     * the one thing this step exists to prevent.
     */
    @Test
    void mustHappen_theSelectedSourceSurvivesIntoAttemptState() throws IOException {
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertTrue(goal.contains("TradeSourceKey.VANILLA"),
                "every production candidate is given explicit provenance at selection");
        assertTrue(goal.contains("attemptSource = candidate.source();"),
                "and beginAttempt keeps it for the walk");
        assertTrue(goal.contains("buyCandidate.source()"),
                "the carried BUY takes the BUYER's source, not the seller's");
        assertEquals(goal.split("plannedOffer = null;", -1).length,
                goal.split("attemptSource = null;", -1).length,
                "and it is cleared wherever the planned offer is - a source must not outlive the "
                        + "offer it describes");
    }

    /** The stale comment step 2 left behind, now that a snapshot carries both coordinates. */
    @Test
    void mustNotHappen_theFlattenedIndexCommentSurvives() throws IOException {
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertFalse(goal.contains("carries\n                                // the flattened "
                        + "cross-villager ranking slot"),
                "funding.buyOffer() carries the real board ref since step 2");
        assertFalse(goal.contains("The flat index is a ranking key; only the"),
                "the old flattened-index explanation no longer describes the code");
    }

    /** Step 3 changes no execution behaviour: the same adapter still owns the transaction. */
    @Test
    void mustHappen_executionStillReachesTheSameAdapter() throws IOException {
        String goal = source("goal/TradeWithVillagerGoal.java");

        assertTrue(goal.contains("VillagerTradeAdapter.performTrade(backpack, target, plannedOffer)"),
                "no source-specific execution path exists yet - that is step 4/5");
        assertTrue(source("village/trade/VillagerTradeAdapter.java")
                        .contains("preservingAttribution(villager)"),
                "and V2-DEF-001 preservation is untouched");
    }
}
