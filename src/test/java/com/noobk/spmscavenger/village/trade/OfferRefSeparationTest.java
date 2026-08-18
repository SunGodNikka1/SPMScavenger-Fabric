package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

/**
 * D-VR-077 step 2 — <b>source-local resolution identity and round-local ranking are different
 * coordinates.</b>
 *
 * <p>They shared an {@code int} until now, and the collision was real rather than theoretical: two
 * villagers both own board index {@code 0}, so any code comparing "the same offer" by index could
 * match a candidate on one merchant against a candidate on another. It stayed invisible because
 * {@code TradeWithVillagerGoal} quietly built a second snapshot whose index field carried the flat
 * ranking slot, and kept the real one in a side map.
 *
 * <p>Trade Everything is not installed and not referenced. If this refactor changes vanilla
 * behaviour, the vanilla suite is what has to catch it.
 */
class OfferRefSeparationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final java.util.function.Function<ItemStack, OptionalInt> SPARE =
            stack -> OptionalInt.of(0);

    private static OfferSnapshot buy(int boardIndex, int rankOrdinal, int emeralds) {
        return new OfferSnapshot(OfferRef.board(boardIndex), rankOrdinal,
                new ItemStack(Items.EMERALD, emeralds), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f, 0, 0, true);
    }

    private static OfferSnapshot sell(int boardIndex, int rankOrdinal) {
        return new OfferSnapshot(OfferRef.board(boardIndex), rankOrdinal,
                new ItemStack(Items.STICK, 32), ItemStack.EMPTY,
                new ItemStack(Items.EMERALD, 1), 0, 16, 0, 0f, 0, 0, true);
    }

    private static SellFundingLeg legFor(OfferSnapshot offer) {
        return new SellFundingLeg(offer,
                new SellAuthorization(new ItemStack(Items.STICK, 32), 64,
                        com.noobk.spmscavenger.ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey()),
                1, 4);
    }

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade"));
    }

    // ------------------------------------------------------------------ the two coordinates

    @Test
    void mustHappen_twoVillagersMayBothOwnBoardIndexZero() {
        OfferSnapshot fromA = buy(0, 0, 5);
        OfferSnapshot fromB = buy(0, 1, 5);

        assertEquals(fromA.ref(), fromB.ref(), "board index 0 is villager-local and legitimately shared");
        assertNotEquals(fromA.rankOrdinal(), fromB.rankOrdinal(),
                "but they are different candidates in this round, and ranking must tell them apart");
    }

    /**
     * <b>The Step 2 regression, in production shape.</b>
     *
     * <p>The first version of this refactor made {@code covers} compare {@code rankOrdinal}, and the
     * suite went green because no test reproduced how the two snapshots actually reach that call:
     *
     * <pre>
     * selection    the goal ranks across ALL villagers  -> seller's SELL is rankOrdinal 7
     * walk
     * execution    stillAuthorized re-derives from inspectOffers(target), which knows only this
     *              villager and defaults rankOrdinal to the board index -> 0
     * </pre>
     *
     * Same seller, same board slot, same economics, different ordinal — so {@code covers} returned
     * false and the mob refused its own funding sale. Every cross-villager SELL-&gt;BUY chain whose
     * seller did not happen to rank at its own board index would have failed at the boundary, which
     * is most of them.
     *
     * <p>The offers list here is built the way {@code inspectOffers} builds it — ordinal equals
     * position — because that is the shape the previous test failed to have.
     */
    @Test
    void mustHappen_aPlannedSellIsCoveredAfterSingleVillagerReDerivation() {
        OfferSnapshot plannedAcrossRound = sell(0, 7);
        OfferSnapshot reDerivedLocally = sell(0, 0);

        assertTrue(legFor(reDerivedLocally).covers(plannedAcrossRound),
                "same villager, same board ref, same quote - the round ordinal is not identity");
    }

    /** A different board slot on the same villager is a different offer, ordinal notwithstanding. */
    @Test
    void mustNotHappen_aDifferentBoardSlotOnTheSameVillagerIsCovered() {
        assertFalse(legFor(sell(0, 0)).covers(sell(1, 0)),
                "BoardIndex is the source-local resolution identity and it differs");
    }

    /** Same ref, moved price: the quote changed under us and the authorization does not carry. */
    @Test
    void mustNotHappen_aRepricedOfferAtTheSameRefIsCovered() {
        OfferSnapshot repriced = new OfferSnapshot(OfferRef.board(0), 0,
                new ItemStack(Items.STICK, 48), ItemStack.EMPTY,
                new ItemStack(Items.EMERALD, 1), 0, 16, 0, 0f, 0, 0, true);

        assertFalse(legFor(sell(0, 0)).covers(repriced),
                "48 sticks is not the 32-stick sale that was authorized");
    }

    /**
     * The rule, structurally: ordering must not leak into the execution boundary.
     *
     * <p>Weaker than the behavioural tests above and kept anyway, because the defect was introduced
     * by a one-line substitution that read perfectly well.
     */
    @Test
    void mustNotHappen_sellFundingLegConsultsTheRoundOrdinal() throws IOException {
        assertFalse(source("village/trade/SellFundingLeg.java").contains("rankOrdinal"),
                "rankOrdinal is ordering only; execution correspondence is ref + semantics");
    }

    @Test
    void mustNotHappen_aNegativeBoardIndexIsConstructible() {
        assertThrows(IllegalArgumentException.class, () -> OfferRef.board(-1),
                "a sentinel index is not a way to say 'not addressable' - that is what sealing is for");
        assertThrows(IllegalArgumentException.class, () -> new OfferRef.BoardIndex(-9999));
    }

    @Test
    void mustHappen_theBoardRefSurvivesRanking() {
        OfferSnapshot onBoardAtThree = buy(3, 3, 5);
        OfferSnapshot ranked = onBoardAtThree.withRankOrdinal(17);

        assertEquals(OfferRef.board(3), ranked.ref(),
                "where it lives on the villager's board is untouched by where it sorts");
        assertEquals(17, ranked.rankOrdinal());
        assertEquals(onBoardAtThree.costA().getCount(), ranked.costA().getCount(),
                "and nothing else about the offer moves");
    }

    // ------------------------------------------------------------------ ranking uses the ordinal

    /**
     * Determinism under duplicate board refs — the case that could not previously be expressed.
     *
     * <p>Two identical purchases from two villagers, both at board index 0. Utility ties, so the
     * tie-break decides, and it must be the round ordinal. Ranking on the board ref would be a coin
     * flip between two equal keys.
     */
    @Test
    void mustHappen_rankingIsDeterministicWhenBoardRefsCollide() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.EMERALD, 16));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buy(0, 4, 5), buy(0, 2, 5)), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, SPARE);

        assertEquals(2, target.buyOffer().rankOrdinal(),
                "the lower ROUND ordinal wins the tie; both board refs are 0 and cannot decide it");
        assertEquals(OfferRef.board(0), target.buyOffer().ref(),
                "and the winner still carries its own board ref for execution");
    }

    // ------------------------------------------------------------------ ownership boundaries

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/noobk/spmscavenger").resolve(relative));
    }

    /**
     * Only the adapter may read a ref as an address. Policy ranks; it does not know where an offer
     * sits in somebody's list.
     */
    @Test
    void mustNotHappen_policyCodeInterpretsABoardRef() throws IOException {
        for (String policy : List.of("village/trade/TradeFundingPlanner.java",
                "village/trade/TradeEvaluationPolicy.java",
                "village/trade/TradeDemandRegistrar.java",
                "village/trade/SellFundingLeg.java")) {
            assertFalse(source(policy).contains("BoardIndex"),
                    policy + " must not interpret a board address - it ranks by rankOrdinal");
        }
        assertTrue(source("village/trade/VillagerTradeAdapter.java")
                        .contains("instanceof OfferRef.BoardIndex"),
                "the adapter is the one place allowed to resolve a ref, and it pattern-matches "
                        + "rather than trusting an int");
    }

    /** V2-DEF-001 must survive the refactor: production still routes notify through the binding. */
    @Test
    void mustHappen_theAttributionBindingSurvivesTheRefactor() throws IOException {
        String adapter = source("village/trade/VillagerTradeAdapter.java");
        assertTrue(adapter.contains("preservingAttribution(villager)"),
                "performTrade must still wrap notifyTrade in the V2-DEF-001 binding");
        // The call shape, not the bare token: performResolvedTrade's javadoc names the forbidden
        // notifier in prose, and a substring test that cannot tell an example from a call site is a
        // test that fails for the wrong reason.
        assertFalse(adapter.contains("villager::notifyTrade)"),
                "and must never hand the raw notifier to executeResolved");
        assertTrue(adapter.contains("performResolvedTrade("),
                "production reaches the transaction through the attribution-owning entry point");
    }
}
