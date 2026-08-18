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
                new ItemStack(Items.IRON_INGOT, 1), 0, 12);
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
     * The latent bug the separation fixes.
     *
     * <p>{@code SellFundingLeg.covers} asks "is this the attempt I authorized?". It compared board
     * indexes, so a leg authorized on one merchant matched an attempt on another whenever both sat
     * at the same board position — which for index 0 is the common case, not the corner case.
     */
    @Test
    void mustNotHappen_aLegOnOneMerchantCoversAnAttemptOnAnother() {
        OfferSnapshot legOffer = new OfferSnapshot(OfferRef.board(0), 0,
                new ItemStack(Items.STICK, 32), ItemStack.EMPTY,
                new ItemStack(Items.EMERALD, 1), 0, 16);
        OfferSnapshot otherMerchantSameSlot = new OfferSnapshot(OfferRef.board(0), 7,
                new ItemStack(Items.STICK, 32), ItemStack.EMPTY,
                new ItemStack(Items.EMERALD, 1), 0, 16);
        SellFundingLeg leg = new SellFundingLeg(legOffer,
                new SellAuthorization(new ItemStack(Items.STICK, 32), 64,
                        com.noobk.spmscavenger.ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey()),
                1, 4);

        assertTrue(leg.covers(legOffer), "the authorized candidate is still covered");
        assertFalse(leg.covers(otherMerchantSameSlot),
                "identical offer, identical board slot, DIFFERENT merchant - not the same attempt");
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
        assertFalse(adapter.contains("villager::notifyTrade"),
                "and must never hand the raw notifier to executeResolved");
    }
}
