package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * V2-A closure — the two-cost path end to end through the adapter, not just the allocator.
 *
 * <p>Two-cost offers are part of the locked V2-A transaction contract, so the whole chain has to be
 * proven: {@code MerchantOffer(A+B)} → {@code OfferSnapshot} → {@code matchesLive} →
 * {@code canAfford} → {@code TradeTransaction} → both costs removed exactly, result inserted
 * exactly, uses +1 exactly once.
 *
 * <p>{@code executeAgainst} is the real {@code performTrade} body — {@code performTrade} only
 * supplies the villager's offer list and its {@code notifyTrade}. So this exercises the shipping
 * path, not a parallel one.
 */
class TwoCostTradeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** 24 wheat + 4 emeralds → 1 enchanted book, 12 uses. */
    private static MerchantOffer twoCostOffer() {
        return new MerchantOffer(
                new ItemCost(Items.WHEAT, 24),
                Optional.of(new ItemCost(Items.EMERALD, 4)),
                new ItemStack(Items.BOOK, 1),
                0, 12, 0, 0f);
    }

    private static MerchantOffers offersOf(MerchantOffer... offers) {
        MerchantOffers list = new MerchantOffers();
        for (MerchantOffer offer : offers) {
            list.add(offer);
        }
        return list;
    }

    private record Notified(List<MerchantOffer> calls) {
        void accept(MerchantOffer offer) {
            calls.add(offer);
            offer.increaseUses();
        }
    }

    @Test
    void mustHappen_aTwoCostTradeDebitsBothCostsAcrossSlotsExactlyOnce() {
        MerchantOffer live = twoCostOffer();
        MerchantOffers offers = offersOf(live);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        assertTrue(snapshot.hasSecondCost(), "fixture really is a two-cost offer");
        assertTrue(snapshot.matchesLive(live));

        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 16));
        backpack.setItem(1, new ItemStack(Items.EMERALD, 3));
        backpack.setItem(4, new ItemStack(Items.WHEAT, 10));   // A spans slots 0 and 4
        backpack.setItem(6, new ItemStack(Items.EMERALD, 1));  // B spans slots 1 and 6

        assertTrue(VillagerTradeAdapter.canAfford(backpack, snapshot));

        Notified notified = new Notified(new ArrayList<>());
        VillagerTradeAdapter.TradeResult result =
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept);

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED, result);
        assertEquals(2, backpack.countItem(Items.WHEAT), "24 of 26 wheat spent, across two slots");
        assertEquals(0, backpack.countItem(Items.EMERALD), "all 4 emeralds spent, across two slots");
        assertEquals(1, backpack.countItem(Items.BOOK), "exact result inserted");

        assertEquals(1, notified.calls().size(), "notifyTrade exactly once");
        assertSame(live, notified.calls().get(0), "notified with the live offer, not the snapshot");
        assertEquals(1, live.getUses(), "uses +1 exactly once");
    }

    /**
     * The failure that must not half-spend: cost A is affordable and cost B is not.
     *
     * <p>Cost A is debited on the staging array before B is even attempted, so this is precisely the
     * case where a non-staged implementation would take the wheat and give nothing back.
     */
    @Test
    void mustNotHappen_anAffordableFirstCostIsSpentWhenTheSecondFails() {
        MerchantOffer live = twoCostOffer();
        MerchantOffers offers = offersOf(live);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 24));   // A: exactly affordable
        backpack.setItem(1, new ItemStack(Items.EMERALD, 3));  // B: one short

        Notified notified = new Notified(new ArrayList<>());
        VillagerTradeAdapter.TradeResult result =
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept);

        assertEquals(VillagerTradeAdapter.TradeResult.CANNOT_AFFORD, result);
        assertEquals(24, backpack.countItem(Items.WHEAT), "cost A must not be spent");
        assertEquals(3, backpack.countItem(Items.EMERALD));
        assertEquals(0, backpack.countItem(Items.BOOK), "no result");
        assertTrue(notified.calls().isEmpty(), "no notifyTrade");
        assertEquals(0, live.getUses(), "the offer must not record a use");
    }

    /** A mismatched second cost is a different item, not merely a smaller count. */
    @Test
    void mustNotHappen_aWrongSecondCostItemIsAccepted() {
        MerchantOffer live = twoCostOffer();
        MerchantOffers offers = offersOf(live);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 24));
        backpack.setItem(1, new ItemStack(Items.DIAMOND, 16));

        Notified notified = new Notified(new ArrayList<>());
        assertEquals(VillagerTradeAdapter.TradeResult.CANNOT_AFFORD,
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept));
        assertEquals(24, backpack.countItem(Items.WHEAT));
        assertEquals(16, backpack.countItem(Items.DIAMOND));
        assertEquals(0, live.getUses());
    }

    /**
     * Both costs draw from the same staging array, so an offer costing the same item twice cannot be
     * paid once and counted twice.
     */
    @Test
    void mustNotHappen_oneStackPaysBothCosts() {
        MerchantOffer sameItemTwice = new MerchantOffer(
                new ItemCost(Items.EMERALD, 8),
                Optional.of(new ItemCost(Items.EMERALD, 8)),
                new ItemStack(Items.BOOK, 1),
                0, 12, 0, 0f);
        MerchantOffers offers = offersOf(sameItemTwice);
        OfferSnapshot snapshot = OfferSnapshot.of(0, sameItemTwice);

        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.EMERALD, 8)); // enough for A alone, not for A+B

        Notified notified = new Notified(new ArrayList<>());
        assertEquals(VillagerTradeAdapter.TradeResult.CANNOT_AFFORD,
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept));
        assertEquals(8, backpack.countItem(Items.EMERALD), "nothing spent");

        backpack.setItem(1, new ItemStack(Items.EMERALD, 8)); // now 16 total
        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept));
        assertEquals(0, backpack.countItem(Items.EMERALD), "both costs spent");
        assertEquals(1, notified.calls().size());
    }

    /** A two-cost offer whose result cannot fit must return the payment untouched. */
    @Test
    void mustNotHappen_bothCostsAreSpentWhenTheResultCannotFit() {
        MerchantOffer live = twoCostOffer();
        MerchantOffers offers = offersOf(live);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        // The slots must stay OCCUPIED after both debits, or paying empties a slot and the result
        // fits - which is what the first version of this test got wrong: it paid with exactly-sized
        // stacks, both slots emptied, the book fitted, and TRADED was the correct answer.
        SimpleContainer backpack = new SimpleContainer(2);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 64));
        backpack.setItem(1, new ItemStack(Items.EMERALD, 64));

        Notified notified = new Notified(new ArrayList<>());
        VillagerTradeAdapter.TradeResult result =
                VillagerTradeAdapter.executeAgainst(backpack, offers, snapshot, notified::accept);

        assertEquals(VillagerTradeAdapter.TradeResult.NO_ROOM, result,
                "both slots remain occupied by the change, so the book has nowhere to go");
        assertEquals(64, backpack.countItem(Items.WHEAT), "payment returned in full");
        assertEquals(64, backpack.countItem(Items.EMERALD));
        assertTrue(notified.calls().isEmpty());
        assertEquals(0, live.getUses());
    }

    /** Revalidation covers the second cost too, not only A and the result. */
    @Test
    void mustNotHappen_aChangedSecondCostPassesRevalidation() {
        OfferSnapshot agreed = OfferSnapshot.of(0, twoCostOffer());

        MerchantOffer dearerB = new MerchantOffer(
                new ItemCost(Items.WHEAT, 24),
                Optional.of(new ItemCost(Items.EMERALD, 6)),
                new ItemStack(Items.BOOK, 1),
                0, 12, 0, 0f);
        MerchantOffers offers = offersOf(dearerB);

        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 64));
        backpack.setItem(1, new ItemStack(Items.EMERALD, 64));

        Notified notified = new Notified(new ArrayList<>());
        assertEquals(VillagerTradeAdapter.TradeResult.OFFER_CHANGED,
                VillagerTradeAdapter.executeAgainst(backpack, offers, agreed, notified::accept),
                "cost B rose from 4 to 6 - the mob must not silently pay the new price");
        assertEquals(64, backpack.countItem(Items.EMERALD));
        assertEquals(0, dearerB.getUses());
    }
}
