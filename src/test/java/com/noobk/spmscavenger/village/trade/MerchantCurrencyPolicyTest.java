package com.noobk.spmscavenger.village.trade;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCurrencyProvider;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-TE maintenance — planning and execution share one denomination truth. */
class MerchantCurrencyPolicyTest {

    private static final MerchantCurrencyPolicy TE = new TradeEverythingCurrencyProvider();
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "currency_parity_test");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SimpleContainer backpack(ItemStack... stacks) {
        SimpleContainer backpack = new SimpleContainer(8);
        for (int i = 0; i < stacks.length; i++) {
            backpack.setItem(i, stacks[i]);
        }
        return backpack;
    }

    private static MerchantOffer buy(int costA, int costB) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, costA),
                costB <= 0 ? Optional.empty() : Optional.of(new ItemCost(Items.EMERALD, costB)),
                new ItemStack(Items.IRON_PICKAXE), 0, 12, 0, 0f);
    }

    private static OfferSnapshot snapshot(int ordinal, MerchantOffer offer) {
        return OfferSnapshot.of(ordinal, offer);
    }

    private static VillagerTradeAdapter.TradeResult execute(
            SimpleContainer backpack, MerchantOffer offer, AtomicInteger notifications) {
        return VillagerTradeAdapter.executeResolved(
                backpack, offer, notified -> {
                    assertSame(offer, notified, "the exact Q2 object must reach notifyTrade");
                    notifications.incrementAndGet();
                }, TE);
    }

    @Test
    void mustHappen_oneBlockFundsExactlyNineEmeralds() {
        SimpleContainer backpack = backpack(new ItemStack(Items.EMERALD_BLOCK));
        AtomicInteger notifications = new AtomicInteger();

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                execute(backpack, buy(9, 0), notifications));
        assertEquals(0, backpack.countItem(Items.EMERALD_BLOCK));
        assertEquals(0, backpack.countItem(Items.EMERALD));
        assertEquals(1, backpack.countItem(Items.IRON_PICKAXE));
        assertEquals(1, notifications.get());
    }

    @Test
    void mustHappen_fourLoosePlusOneBlockFundsTenAndLeavesThree() {
        SimpleContainer backpack = backpack(
                new ItemStack(Items.EMERALD, 4), new ItemStack(Items.EMERALD_BLOCK));

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                execute(backpack, buy(10, 0), new AtomicInteger()));
        assertEquals(0, backpack.countItem(Items.EMERALD_BLOCK));
        assertEquals(3, backpack.countItem(Items.EMERALD));
    }

    @Test
    void mustHappen_oneBlockFundsEightAndLeavesOne() {
        SimpleContainer backpack = backpack(new ItemStack(Items.EMERALD_BLOCK));

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                execute(backpack, buy(8, 0), new AtomicInteger()));
        assertEquals(1, backpack.countItem(Items.EMERALD));
    }

    @Test
    void mustHappen_bothCostSlotsShareOneNormalization() {
        SimpleContainer backpack = backpack(
                new ItemStack(Items.EMERALD, 4), new ItemStack(Items.EMERALD_BLOCK));

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                execute(backpack, buy(6, 4), new AtomicInteger()));
        assertEquals(3, backpack.countItem(Items.EMERALD));
        assertEquals(0, backpack.countItem(Items.EMERALD_BLOCK));
    }

    @Test
    void mustNotHappen_failedOutputInsertionCommitsStagedNormalization() {
        SimpleContainer backpack = new SimpleContainer(2);
        backpack.setItem(0, new ItemStack(Items.EMERALD_BLOCK));
        backpack.setItem(1, new ItemStack(Items.STONE, 64));
        AtomicInteger notifications = new AtomicInteger();

        assertEquals(VillagerTradeAdapter.TradeResult.NO_ROOM,
                execute(backpack, buy(8, 0), notifications));
        assertEquals(1, backpack.countItem(Items.EMERALD_BLOCK),
                "the real denomination remains unchanged when staged insertion fails");
        assertEquals(0, backpack.countItem(Items.EMERALD));
        assertEquals(0, notifications.get());
    }

    @Test
    void mustHappen_blockPayoutFundsPlanningInEmeraldUnits() {
        WorkDemandPolicy.MaterialDemand demand = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, CONSUMER);
        OfferSnapshot purchase = snapshot(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 10), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 12, 0, 0f));
        OfferSnapshot fundingSale = snapshot(1, new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 1), Optional.empty(),
                new ItemStack(Items.EMERALD_BLOCK, 2), 0, 12, 0, 0f));
        SimpleContainer backpack = backpack(new ItemStack(Items.OAK_LOG, 64));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                demand, List.of(purchase, fundingSale), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, ignored -> OptionalInt.of(0), TE);

        assertNotNull(target);
        assertEquals(10, target.deficit().emeraldsNeeded());
        assertNotNull(target.sellLeg());
        assertEquals(18, target.sellLeg().emeraldsPerUse());
        assertTrue(target.actionable());

        TradeFundingPlanner.FundingTarget vanilla = TradeFundingPlanner.chooseFundingTarget(
                demand, List.of(purchase, fundingSale), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY, ignored -> OptionalInt.of(0),
                VanillaMerchantCurrency.INSTANCE);
        assertNotNull(vanilla);
        assertNull(vanilla.sellLeg(), "vanilla must not reinterpret emerald blocks as currency");
    }

    @Test
    void mustHappen_saleOutputRemainsABlockUntilPayment() {
        SimpleContainer backpack = backpack(new ItemStack(Items.OAK_LOG));
        MerchantOffer sale = new MerchantOffer(
                new ItemCost(Items.OAK_LOG, 1), Optional.empty(),
                new ItemStack(Items.EMERALD_BLOCK, 2), 0, 12, 0, 0f);

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                execute(backpack, sale, new AtomicInteger()));
        assertEquals(2, backpack.countItem(Items.EMERALD_BLOCK));
        assertEquals(0, backpack.countItem(Items.EMERALD));
        assertEquals(18, TE.liquidity(backpack));
    }

    @Test
    void mustNotHappen_vanillaPolicyGrantsBlockLiquidityOrNormalization() {
        SimpleContainer backpack = backpack(new ItemStack(Items.EMERALD_BLOCK));
        MerchantOffer offer = buy(9, 0);

        assertEquals(0, VanillaMerchantCurrency.INSTANCE.liquidity(backpack));
        assertFalse(VillagerTradeAdapter.canAfford(
                backpack, snapshot(0, offer), VanillaMerchantCurrency.INSTANCE));
        assertEquals(VillagerTradeAdapter.TradeResult.CANNOT_AFFORD,
                VillagerTradeAdapter.executeResolved(
                        backpack, offer, ignored -> { }, VanillaMerchantCurrency.INSTANCE));
        assertEquals(1, backpack.countItem(Items.EMERALD_BLOCK));
    }
}
