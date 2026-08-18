package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
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
 * V2-A — snapshot identity, exact revalidation, and the structural ban on the live accessors.
 *
 * <p>{@code performTrade} itself needs a live {@code Villager} and therefore a server; what is
 * provable here is everything up to that boundary — which includes the two facts most likely to be
 * got wrong.
 */
class TradeAdapterContractTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MerchantOffer offer(int wheat, int emeralds, int uses, int maxUses) {
        return new MerchantOffer(
                new ItemCost(Items.WHEAT, wheat),
                Optional.empty(),
                new ItemStack(Items.EMERALD, emeralds),
                uses, maxUses, 0, 0f);
    }

    /**
     * The defect this whole slice was designed around: {@code getResult()} hands out the villager's
     * own stack. If a snapshot held that reference, mutating the snapshot's result would rewrite the
     * offer — permanently, and into the save.
     */
    @Test
    void mustNotHappen_theSnapshotAliasesTheLiveOfferResult() {
        MerchantOffer live = offer(20, 1, 0, 12);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        assertFalse(snapshot.result() == live.getResult(),
                "the snapshot must not hold the live result instance");

        snapshot.result().setCount(64);
        assertEquals(1, live.getResult().getCount(),
                "mutating the snapshot must leave the villager's offer untouched");
        assertEquals(1, live.assemble().getCount());
    }

    @Test
    void mustNotHappen_theSnapshotAliasesTheLiveCost() {
        MerchantOffer live = offer(20, 1, 0, 12);
        OfferSnapshot snapshot = OfferSnapshot.of(0, live);

        snapshot.costA().setCount(1);
        assertEquals(20, live.getCostA().getCount());
    }

    /** Exact correspondence, and prices move without items changing — so counts must be compared. */
    @Test
    void mustHappen_revalidationRejectsAChangedPrice() {
        MerchantOffer live = offer(20, 1, 0, 12);
        OfferSnapshot agreed = OfferSnapshot.of(0, live);
        assertTrue(agreed.matchesLive(live));

        MerchantOffer dearer = offer(24, 1, 0, 12);
        assertFalse(agreed.matchesLive(dearer),
                "same items, higher count - an item-only check would let the mob overpay");

        MerchantOffer stingier = offer(20, 1, 0, 12);
        stingier.getResult().setCount(1);
        assertTrue(agreed.matchesLive(stingier));
    }

    @Test
    void mustNotHappen_revalidationAcceptsADifferentItem() {
        OfferSnapshot agreed = OfferSnapshot.of(0, offer(20, 1, 0, 12));
        MerchantOffer other = new MerchantOffer(
                new ItemCost(Items.CARROT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f);
        assertFalse(agreed.matchesLive(other));
        assertFalse(agreed.matchesLive(null));
    }

    @Test
    void mustHappen_outOfStockIsRefusedBeforeAnythingElse() {
        OfferSnapshot exhausted = OfferSnapshot.of(0, offer(20, 1, 12, 12));
        assertTrue(exhausted.outOfStock());
        assertFalse(exhausted.isTradeable());
        assertFalse(VillagerTradeAdapter.canAfford(
                new net.minecraft.world.SimpleContainer(8), exhausted));
    }

    @Test
    void mustHappen_canAffordSeesAcrossSlots() {
        net.minecraft.world.SimpleContainer backpack = new net.minecraft.world.SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 16));
        backpack.setItem(3, new ItemStack(Items.WHEAT, 4));

        OfferSnapshot twentyWheat = OfferSnapshot.of(0, offer(20, 1, 0, 12));
        assertTrue(VillagerTradeAdapter.canAfford(backpack, twentyWheat));

        backpack.setItem(3, new ItemStack(Items.WHEAT, 3));
        assertFalse(VillagerTradeAdapter.canAfford(backpack, twentyWheat));
    }

    /** {@code canAfford} is a question, and asking it must not change the answer. */
    @Test
    void mustNotHappen_canAffordMutatesTheBackpack() {
        net.minecraft.world.SimpleContainer backpack = new net.minecraft.world.SimpleContainer(8);
        backpack.setItem(0, new ItemStack(Items.WHEAT, 32));

        VillagerTradeAdapter.canAfford(backpack, OfferSnapshot.of(0, offer(20, 1, 0, 12)));

        assertEquals(32, backpack.getItem(0).getCount());
    }

    // ------------------------------------------------------------------ structural

    private static String source(String relative) throws IOException {
        String raw = Files.readString(
                Path.of("src/main/java/com/noobk/spmscavenger/village/trade").resolve(relative));
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
     * The live accessors are banned outright. Calling one compiles, runs, and shows up only as a
     * villager's offer quietly changing — so the guard has to be structural.
     */
    @Test
    void mustNotHappen_anyCodePathReachesTheLiveStacks() throws IOException {
        for (String file : List.of("OfferSnapshot.java", "TradeTransaction.java",
                "VillagerTradeAdapter.java")) {
            String body = source(file);
            assertFalse(body.contains(".getResult()"),
                    file + " must use assemble(); getResult() returns the live result field");
            assertFalse(body.contains(".getBaseCostA()"),
                    file + " must use getCostA(); getBaseCostA() returns the live ItemCost stack");
        }
    }

    /** Gen-1 touches no player-typed merchant API, and no client type. */
    @Test
    void mustNotHappen_theAdapterReachesForPlayerOrMenuApis() throws IOException {
        String body = source("VillagerTradeAdapter.java");
        for (String forbidden : List.of("setTradingPlayer", "MerchantMenu", "updateSpecialPrices",
                "FakePlayer", "ServerPlayer")) {
            assertFalse(body.contains(forbidden), "V2-A must not reach for " + forbidden);
        }
    }

    /**
     * Ordering is the safety property: every refusal happens before the commit, and
     * {@code notifyTrade} happens after it, exactly once.
     */
    @Test
    void mustHappen_commitPrecedesNotifyAndFollowsEveryCheck() throws IOException {
        String body = source("VillagerTradeAdapter.java");

        // Ordering is asserted inside executeAgainst - the real transaction body. performTrade is a
        // wrapper that merely supplies the villager's offer list and its notifyTrade reference, so
        // its mention of `notifyTrade` sits earlier in the file and must not be read as the call.
        int core = body.indexOf("static TradeResult executeAgainst");
        assertTrue(core > 0, "the transaction core exists");
        String transaction = body.substring(core);

        int insert = transaction.indexOf("TradeTransaction.insert");
        int commit = transaction.indexOf("TradeTransaction.commit");
        int notify = transaction.indexOf("notify.accept");

        assertTrue(insert > 0 && commit > insert,
                "the result must be preflighted before the backpack is written");
        assertTrue(notify > commit,
                "the notify must follow the commit, so a villager cannot record an unpaid trade");
        assertEquals(1, transaction.split("notify.accept", -1).length - 1,
                "exactly one notify call site in the transaction");
        assertEquals(1, body.split("notifyTrade", -1).length - 1,
                "exactly one binding of the villager's notifyTrade");

        for (String check : List.of("OFFER_GONE", "OFFER_CHANGED", "OUT_OF_STOCK", "CANNOT_AFFORD",
                "NO_ROOM")) {
            int refusal = transaction.indexOf("return TradeResult." + check);
            assertTrue(refusal > 0 && refusal < commit,
                    check + " must be refused before anything is committed");
        }
    }

    // ------------------------------------------- P0-2 the extracted common executor

    /**
     * P0-2 split {@code executeAgainst} into <b>resolution</b> (board index + snapshot match) and
     * {@code executeResolved} (stage, debit, preflight, commit, notify). The vanilla path still runs
     * the second half through this method, and a Trade Everything path would reach it having
     * resolved by re-quoting instead.
     *
     * <p>Pinned directly so the seam has its own contract rather than being covered only through the
     * board-index caller: {@code executeResolved} must be the sole transaction owner, and there must
     * never be a second staging/commit implementation for detached offers.
     */
    @Test
    void mustHappen_theResolvedExecutorCommitsAndNotifiesExactlyOnce() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        MerchantOffer detached = new MerchantOffer(new ItemCost(Items.OAK_LOG, 22),
                Optional.empty(), new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0f);
        java.util.List<MerchantOffer> notified = new java.util.ArrayList<>();

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeResolved(backpack, detached, notified::add));

        assertEquals(42, com.noobk.spmscavenger.ScavengerCrafting.count(backpack, Items.OAK_LOG),
                "exactly the quoted cost is debited, once");
        assertEquals(1, com.noobk.spmscavenger.ScavengerCrafting.count(backpack, Items.EMERALD),
                "exactly the quoted result is inserted, once");
        assertEquals(1, notified.size(), "notifyTrade fires exactly once");
        assertSame(detached, notified.get(0),
                "and with the SAME object the source produced - TE marks synthetic offers with a "
                        + "mixin-injected instance field, so passing a reconstruction would strip "
                        + "the marker and silence TE's afterTrade hook");
    }

    /** The mob must keep its payment when the result cannot land. Detached changes nothing here. */
    @Test
    void mustNotHappen_theResolvedExecutorDebitsWhenTheResultCannotFit() {
        SimpleContainer full = new SimpleContainer(1);
        full.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        MerchantOffer detached = new MerchantOffer(new ItemCost(Items.OAK_LOG, 22),
                Optional.empty(), new ItemStack(Items.EMERALD, 1), 0, 999_999, 0, 0f);
        java.util.List<MerchantOffer> notified = new java.util.ArrayList<>();

        assertEquals(VillagerTradeAdapter.TradeResult.NO_ROOM,
                VillagerTradeAdapter.executeResolved(full, detached, notified::add));
        assertEquals(64, com.noobk.spmscavenger.ScavengerCrafting.count(full, Items.OAK_LOG),
                "the staged debit must not reach the real backpack");
        assertEquals(0, notified.size(), "and no trade may be notified");
    }
}
