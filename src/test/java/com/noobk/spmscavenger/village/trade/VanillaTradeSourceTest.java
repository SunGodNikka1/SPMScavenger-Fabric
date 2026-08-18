package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * D-VR-077 step 5 — vanilla board resolution, now owned by the source rather than the adapter.
 *
 * <p>Board semantics are tested through {@code resolveOnBoard}/{@code snapshot}, the entity-free
 * halves — the same split, for the same reason, as {@code executeAgainst} being carved out of
 * {@code performTrade}. Requiring a live {@code Villager} would mean not testing any of this.
 */
class VanillaTradeSourceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MerchantOffer offer(int emeralds, int uses, int maxUses, int xp, int demand) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), uses, maxUses, xp, 0f, demand);
    }

    private static MerchantOffers board(MerchantOffer... offers) {
        MerchantOffers list = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            list.add(offer);
        }
        return list;
    }

    // ------------------------------------------------------------------ resolution

    /**
     * The exact live object, not a copy.
     *
     * <p>{@code executeResolved} passes what it is given straight to {@code notifyTrade}, and Trade
     * Everything marks synthetic offers with a mixin-injected instance field that a reconstruction
     * silently drops. Vanilla has no marker to lose, so this is where the contract gets pinned while
     * it is still cheap.
     */
    @Test
    void mustHappen_resolutionReturnsTheExactLiveObject() {
        MerchantOffer live = offer(5, 0, 12, 0, 0);
        MerchantOffers board = board(offer(1, 0, 12, 0, 0), live);
        OfferSnapshot planned = OfferSnapshot.of(1, live);

        assertSame(live, VanillaTradeSource.resolveOnBoard(board, planned).orElseThrow(),
                "the board's own object, so nothing downstream can be handed a lookalike");
    }

    @Test
    void mustHappen_theBoardIndexAddressesTheRightOffer() {
        MerchantOffers board = board(offer(1, 0, 12, 0, 0), offer(5, 0, 12, 0, 0));

        assertEquals(5, VanillaTradeSource
                .resolveOnBoard(board, OfferSnapshot.of(1, board.get(1)))
                .orElseThrow().getCostA().getCount());
        assertEquals(1, VanillaTradeSource
                .resolveOnBoard(board, OfferSnapshot.of(0, board.get(0)))
                .orElseThrow().getCostA().getCount());
    }

    /** A ref this source does not own is refused, never guessed at. */
    @Test
    void mustNotHappen_aRequoteResolvesOnTheVanillaBoard() {
        MerchantOffers board = board(offer(5, 0, 12, 0, 0));
        OfferSnapshot planned = new OfferSnapshot(
                OfferRef.requote(new ItemStack(Items.OAK_LOG, 22)), 0,
                new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY,
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f, 0, 0, true);

        assertTrue(VanillaTradeSource.resolveOnBoard(board, planned).isEmpty(),
                "it belongs to whichever source generated it; guessing is the inference "
                        + "D-VR-077 rejects");
    }

    @Test
    void mustNotHappen_anOutOfRangeIndexResolves() {
        MerchantOffers board = board(offer(5, 0, 12, 0, 0));
        OfferSnapshot planned = OfferSnapshot.of(0, board.get(0));

        assertTrue(VanillaTradeSource
                .resolveOnBoard(board(), planned).isEmpty(), "the board shrank under us");
    }

    @Test
    void mustNotHappen_anOutOfStockOfferResolves() {
        MerchantOffers board = board(offer(5, 12, 12, 0, 0));

        assertTrue(VanillaTradeSource
                        .resolveOnBoard(board, OfferSnapshot.of(0, board.get(0))).isEmpty(),
                "uses == maxUses is not a trade we can perform");
    }

    // ------------------------------------------------------------------ the strictness line

    /**
     * Semantic-only drift is still accepted, exactly as {@code matchesLive} accepts it.
     *
     * <p>This is the whole reason vanilla revalidation was not turned into the P0-1 comparator.
     * {@code xp} and {@code demand} move during a walk, the live object is what executes, and
     * rejecting on them would abort trades that succeed today.
     */
    @Test
    void mustHappen_semanticOnlyDriftStillResolves() {
        MerchantOffers board = board(offer(5, 0, 12, 0, 0));
        OfferSnapshot planned = OfferSnapshot.of(0, board.get(0));

        MerchantOffers drifted = board(offer(5, 0, 12, 9, 6));
        Optional<MerchantOffer> resolved = VanillaTradeSource.resolveOnBoard(drifted, planned);

        assertTrue(resolved.isPresent(), "same effective cost and result - still the same trade");
        assertEquals(9, resolved.orElseThrow().getXp(), "and the live object's own state executes");
    }

    /** Effective cost drift is a different trade, and must be refused. */
    @Test
    void mustNotHappen_effectiveCostDriftResolves() {
        MerchantOffers board = board(offer(5, 0, 12, 0, 0));
        OfferSnapshot planned = OfferSnapshot.of(0, board.get(0));

        assertTrue(VanillaTradeSource.resolveOnBoard(board(offer(7, 0, 12, 0, 0)), planned).isEmpty(),
                "7 emeralds is not the 5-emerald purchase that was authorized");
    }

    // ------------------------------------------------------------------ snapshotting

    @Test
    void mustHappen_everyBoardOfferBecomesAnAddressableSnapshot() {
        List<OfferSnapshot> snapshots =
                VanillaTradeSource.snapshot(board(offer(1, 0, 12, 0, 0), offer(5, 0, 12, 0, 0)));

        assertEquals(2, snapshots.size());
        assertEquals(OfferRef.board(0), snapshots.get(0).ref());
        assertEquals(OfferRef.board(1), snapshots.get(1).ref());
        assertEquals(0, snapshots.get(0).rankOrdinal(),
                "a single-villager inspection has no round context, so ordinal defaults to index");
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void mustHappen_theRegistryResolvesCarriedProvenance() {
        assertSame(VanillaTradeSource.INSTANCE, TradeSources.of(TradeSourceKey.VANILLA));
        assertEquals(TradeSourceKey.VANILLA, VanillaTradeSource.INSTANCE.key());
        assertEquals(1, TradeSources.all().size(), "one source until step 6");
    }

    /** Market truth must not learn about sleep, player sessions, or liveness policy. */
    @Test
    void mustNotHappen_theSourceAppliesPhysicalLegality() throws java.io.IOException {
        // Comments stripped first. A structural test that cannot tell a call site from a javadoc
        // sentence explaining why the call must not exist fails for the wrong reason - which this
        // one did, on the paragraph naming the very thing it forbids.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/noobk/spmscavenger/village/trade/VanillaTradeSource.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");

        assertFalse(source.contains("isSleeping()"),
                "sleep is physical legality and belongs to the executor");
        assertFalse(source.contains("getTradingPlayer()"),
                "so is a human customer - and step 6 would otherwise have to relearn both");
        assertFalse(source.contains("VillagerTradeAdapter"),
                "and the source must not reach back into the transaction owner");
    }
}
