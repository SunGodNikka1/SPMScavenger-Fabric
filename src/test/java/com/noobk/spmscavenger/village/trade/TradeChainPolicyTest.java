package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.village.trade.TradeChainPolicy.ChainFacts;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy.ChainOutcome;
import com.noobk.spmscavenger.village.trade.TradeChainPolicy.Termination;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** V2-D — the transient SELL → BUY chain, and the three stupid behaviours it must make unreachable. */
class TradeChainPolicyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation MENDING_BOOK =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "mending_book");

    private static TradeChainPlan plan(long now) {
        return TradeChainPlan.forConsumer(
                MENDING_BOOK, BuiltInRegistries.ITEM.getKey(Items.BOOK), 1, now);
    }

    /** The User's worked example: needs 9, holds 7, each sell pays 1, owns 64 disposable wheat. */
    @Test
    void mustHappen_sellsOnlyWhatTheBoundedPurchaseNeeds() {
        ChainOutcome outcome = TradeChainPolicy.evaluate(
                plan(0L),
                new ChainFacts(true, 0, 7, 9, 1, 64),
                10L);

        assertTrue(outcome.active());
        assertEquals(TradeChainPlan.Step.SELL_TO_FUND, outcome.plan().step());
        assertEquals(2, outcome.requiredSellUses(),
                "deficit 2 at 1 emerald per sell - not 64 sells because wheat is disposable");
        assertFalse(outcome.sellBlocked());
    }

    @Test
    void mustHappen_sellUsesRoundUpForLumpyOffers() {
        // Deficit 5, each sell pays 2 -> three sells (the last overshoots by one emerald).
        assertEquals(3, TradeChainPolicy.evaluate(
                plan(0L), new ChainFacts(true, 0, 4, 9, 2, 64), 10L).requiredSellUses());
    }

    // ---------------------------------------------------------------- defect probe 1

    /**
     * `ARCHITECTURE_DEFECT` probe: over-selling after the deficit is satisfied.
     *
     * <p>Unreachable by construction — {@code requiredSellUses} is derived from the deficit, so when
     * the deficit is zero there is no quantity to over-sell.
     */
    @Test
    void mustNotHappen_sellingContinuesAfterTheDeficitIsSatisfied() {
        ChainOutcome funded = TradeChainPolicy.evaluate(
                plan(0L), new ChainFacts(true, 0, 9, 9, 1, 64), 10L);

        assertEquals(TradeChainPlan.Step.BUY_TARGET, funded.plan().step());
        assertEquals(0, funded.requiredSellUses(), "nothing further to sell");

        ChainOutcome surplus = TradeChainPolicy.evaluate(
                plan(0L), new ChainFacts(true, 0, 40, 9, 1, 64), 10L);
        assertEquals(0, surplus.requiredSellUses(), "holding more than needed sells nothing");
    }

    // ---------------------------------------------------------------- defect probe 2

    /** `ARCHITECTURE_DEFECT` probe: buying on after the external consumer disappeared. */
    @Test
    void mustNotHappen_theChainContinuesWithoutItsConsumer() {
        ChainOutcome outcome = TradeChainPolicy.evaluate(
                plan(0L),
                new ChainFacts(false, 0, 64, 9, 1, 64), // funded, but nobody wants it
                10L);

        assertTrue(outcome.terminated());
        assertEquals(Termination.CONSUMER_GONE, outcome.termination());
        assertEquals(0, outcome.requiredSellUses());
    }

    // ---------------------------------------------------------------- defect probe 3

    /**
     * `ARCHITECTURE_DEFECT` probe: SELL regenerating forever because its own emerald output looks
     * like a new demand.
     *
     * <p>Emeralds are never a demand here — the only emerald appetite is one bounded deficit, and it
     * shrinks as emeralds arrive. Selling 200 times cannot restart it.
     */
    @Test
    void mustNotHappen_sellRecreatesItselfFromItsOwnOutput() {
        TradeChainPlan chain = plan(0L);
        int emeralds = 0;
        int wheat = 64;

        for (int tick = 1; tick <= 200; tick++) {
            ChainOutcome outcome = TradeChainPolicy.evaluate(
                    chain, new ChainFacts(true, 0, emeralds, 9, 1, wheat), tick);
            if (outcome.terminated()) {
                break;
            }
            chain = outcome.plan();
            if (outcome.requiredSellUses() > 0) {
                emeralds++;   // one successful sell
                wheat -= 20;
            }
        }

        assertEquals(9, emeralds, "sold exactly to the deficit and then stopped, not 200 times");
        assertEquals(TradeChainPlan.Step.BUY_TARGET, chain.step());
        assertEquals(64 - 9 * 20, wheat);
    }

    // ---------------------------------------------------------------- requirements

    /** Req 9: obtained elsewhere ends the chain rather than funding a purchase nobody needs. */
    @Test
    void mustHappen_theChainTerminatesWhenTheTargetArrivesFromElsewhere() {
        ChainOutcome outcome = TradeChainPolicy.evaluate(
                plan(0L), new ChainFacts(true, 1, 0, 9, 1, 64), 10L);

        assertEquals(Termination.TARGET_OBTAINED_ELSEWHERE, outcome.termination());
    }

    @Test
    void mustHappen_theChainExpires() {
        TradeChainPlan chain = plan(0L);
        assertTrue(TradeChainPolicy.evaluate(chain, new ChainFacts(true, 0, 0, 9, 1, 64),
                TradeChainPlan.DEFAULT_LIFETIME_TICKS).terminated());
        assertEquals(Termination.EXPIRED, TradeChainPolicy.evaluate(
                chain, new ChainFacts(true, 0, 0, 9, 1, 64),
                TradeChainPlan.DEFAULT_LIFETIME_TICKS + 1).termination());
    }

    /** Req 11: a failed sell changes nothing, because advancement reads emeralds held. */
    @Test
    void mustNotHappen_aFailedSellAdvancesTheChain() {
        TradeChainPlan chain = plan(0L);
        ChainFacts unchanged = new ChainFacts(true, 0, 7, 9, 1, 64);

        for (int attempt = 0; attempt < 10; attempt++) {
            ChainOutcome outcome = TradeChainPolicy.evaluate(chain, unchanged, 10L + attempt);
            assertEquals(TradeChainPlan.Step.SELL_TO_FUND, outcome.plan().step());
            assertEquals(2, outcome.requiredSellUses(), "still two, however many attempts failed");
        }
    }

    /** Req 7: SPM eating a sellable item shrinks the stock; the chain reports, it does not lock out. */
    @Test
    void mustHappen_insufficientDisposableStockIsReportedNotFatal() {
        ChainOutcome outcome = TradeChainPolicy.evaluate(
                plan(0L), new ChainFacts(true, 0, 7, 9, 1, 1), 10L);

        assertTrue(outcome.active(), "still a live chain");
        assertTrue(outcome.sellBlocked(), "reported as blocked, not terminated");
        assertEquals(2, outcome.requiredSellUses());
    }

    /** Reqs 3 and 4: identity survives every step transition. */
    @Test
    void mustHappen_consumerAndDesiredOutputSurviveEveryStep() {
        TradeChainPlan chain = plan(0L);
        ChainOutcome selling = TradeChainPolicy.evaluate(
                chain, new ChainFacts(true, 0, 0, 9, 1, 64), 10L);
        ChainOutcome buying = TradeChainPolicy.evaluate(
                selling.plan(), new ChainFacts(true, 0, 9, 9, 1, 64), 20L);

        for (TradeChainPlan step : List.of(chain, selling.plan(), buying.plan())) {
            assertEquals(MENDING_BOOK, step.consumerKey());
            assertEquals(BuiltInRegistries.ITEM.getKey(Items.BOOK), step.desiredOutput());
            assertEquals(1, step.targetHeldQuantity());
        }
    }

    // ---------------------------------------------------------------- MAIBS timeline

    /**
     * The economic state machine over time, exactly as specified: eat, fund elsewhere, obtain
     * elsewhere, and then stay quiet.
     */
    @Test
    void mustHappen_theChainTracksAChangingWorldAndThenStaysDead() {
        TradeChainPlan chain = plan(0L);

        // T+1 created, needs 9, holds 0, 64 wheat -> 9 sells
        ChainOutcome t1 = TradeChainPolicy.evaluate(
                chain, new ChainFacts(true, 0, 0, 9, 1, 64), 1L);
        assertEquals(9, t1.requiredSellUses());

        // T+21 SPM ate sellable food; disposable falls to 3 -> still 9 needed, now blocked
        ChainOutcome t21 = TradeChainPolicy.evaluate(
                t1.plan(), new ChainFacts(true, 0, 0, 9, 1, 3), 21L);
        assertEquals(9, t21.requiredSellUses());
        assertTrue(t21.sellBlocked(), "recalculated against the smaller stock, not locked out");

        // T+41 emeralds arrived from elsewhere -> sell requirement shrinks
        ChainOutcome t41 = TradeChainPolicy.evaluate(
                t21.plan(), new ChainFacts(true, 0, 8, 9, 1, 3), 41L);
        assertEquals(1, t41.requiredSellUses());
        assertFalse(t41.sellBlocked());

        // T+61 the book arrived from elsewhere -> the whole chain ends
        ChainOutcome t61 = TradeChainPolicy.evaluate(
                t41.plan(), new ChainFacts(true, 1, 8, 9, 1, 3), 61L);
        assertEquals(Termination.TARGET_OBTAINED_ELSEWHERE, t61.termination());

        // T+200 no resurrection: a terminated chain has no plan to re-evaluate
        assertTrue(TradeChainPolicy.evaluate(null,
                new ChainFacts(true, 0, 8, 9, 1, 3), 200L).terminated());
    }

    // ---------------------------------------------------------------- expendability

    @Test
    void mustNotHappen_equipmentBecomesSellStock() {
        assertEquals(0, SellExpendabilityPolicy.disposableUnits(
                new ItemStack(Items.WOODEN_PICKAXE), 3, 0, ItemStack.EMPTY, ItemStack.EMPTY),
                "durability marks an investment, whether the buyer is a furnace or a villager");
    }

    @Test
    void mustHappen_reservedUnitsAreNotDisposable() {
        assertEquals(24, SellExpendabilityPolicy.disposableUnits(
                new ItemStack(Items.WHEAT), 64, 40, ItemStack.EMPTY, ItemStack.EMPTY));
        assertEquals(0, SellExpendabilityPolicy.disposableUnits(
                new ItemStack(Items.WHEAT), 30, 40, ItemStack.EMPTY, ItemStack.EMPTY),
                "a craft reserve is not spare because a villager will pay for it");
    }

    @Test
    void mustHappen_affordableSellUsesDivideByTheOfferCost() {
        assertEquals(3, SellExpendabilityPolicy.affordableSellUses(64, 20));
        assertEquals(0, SellExpendabilityPolicy.affordableSellUses(19, 20));
        assertEquals(0, SellExpendabilityPolicy.affordableSellUses(64, 0));
    }

    // ---------------------------------------------------------------- structural

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

    /** Req 5: attempt evidence must never become durable identity. */
    @Test
    void mustNotHappen_thePlanCarriesVillagerOfferOrPath() throws IOException {
        String body = source("TradeChainPlan.java");
        for (String forbidden : List.of("Villager", "OfferSnapshot", "offerIndex", "BlockPos",
                "Path", "anchor")) {
            assertFalse(body.contains(forbidden),
                    "TradeChainPlan must not carry " + forbidden + " - that is attempt evidence");
        }
    }

    /** Reqs 10 and 8: transient by construction, so there is nothing to persist or sweep. */
    @Test
    void mustNotHappen_theChainIsPersistedOrReadsAClock() throws IOException {
        for (String file : List.of("TradeChainPlan.java", "TradeChainPolicy.java")) {
            String body = source(file);
            for (String forbidden : List.of("SavedData", "CompoundTag", "save(", "load(",
                    "getGameTime", "System.currentTimeMillis", "Random")) {
                assertFalse(body.contains(forbidden), file + " must not reference " + forbidden);
            }
        }
    }

    /** No movement, no transaction — V2-E owns both. */
    @Test
    void mustNotHappen_v2dReachesIntoExecution() throws IOException {
        for (String file : List.of("TradeChainPlan.java", "TradeChainPolicy.java",
                "SellExpendabilityPolicy.java")) {
            String body = source(file);
            for (String forbidden : List.of("VillagerTradeAdapter", "performTrade", "notifyTrade",
                    "Level", "getNavigation", "moveTo")) {
                assertFalse(body.contains(forbidden), file + " must not reference " + forbidden);
            }
        }
    }

    /**
     * R6 — the quantity bug, as a behavioural regression on the most ordinary state there is.
     *
     * <p>An iron pickaxe needs 3 ingots. A mob that already holds 1 has a deficit of 2, and R5 opened
     * the chain with the <b>deficit</b> while the policy terminates on <b>held</b>. One purchase later
     * the mob holds 2, {@code 2 >= 2} fires, and the chain stops an ingot short of its purpose. Worse,
     * a chain reopened afterwards (deficit 1, held 2) terminates on its first evaluation — that
     * consumer can never again be finished by trade.
     *
     * <p>Fixtures that start from an empty backpack cannot see this, which is why every previous one
     * missed it: at held 0 the deficit and the threshold are the same number.
     */
    @Test
    void mustNotHappen_owningSomeOfWhatYouNeedTerminatesTheChainEarly() {
        int held = 1;
        int required = 3;
        TradeChainPlan chain = TradeChainPlan.forConsumer(
                MENDING_BOOK, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT),
                held + (required - held), 0L);

        assertEquals(3, chain.targetHeldQuantity(), "an absolute threshold, not the deficit");

        TradeChainPolicy.ChainOutcome afterFirstBuy = TradeChainPolicy.evaluate(
                chain, new ChainFacts(true, 2, 0, 2, 1, 4), 10L);
        assertTrue(afterFirstBuy.active(),
                "2 of 3 is not the target obtained elsewhere - with the deficit as the threshold "
                        + "this is exactly where R5 stopped");

        assertEquals(Termination.TARGET_OBTAINED_ELSEWHERE,
                TradeChainPolicy.evaluate(chain, new ChainFacts(true, 3, 0, 2, 1, 4), 20L)
                        .termination(),
                "and the third ingot is what actually closes it");
    }

    /**
     * The units defect, behaviourally: {@code sellBlocked} compares SELL <b>uses</b> on both sides.
     *
     * <p>R5 passed raw disposable item units into this field, so 61 sticks read as "61 sales
     * available" when a 32-stick offer permits exactly one. The comparison was arithmetically fine
     * and the operands were in different currencies.
     */
    @Test
    void mustHappen_sellBlockedComparesUsesAgainstUses() {
        TradeChainPlan chain = TradeChainPlan.forConsumer(
                MENDING_BOOK, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 0L);

        // Two emeralds needed, one per sale: two uses required.
        assertTrue(TradeChainPolicy.evaluate(chain, new ChainFacts(true, 0, 0, 2, 1, 1), 10L)
                        .sellBlocked(),
                "one affordable use cannot fund a two-use deficit");
        assertFalse(TradeChainPolicy.evaluate(chain, new ChainFacts(true, 0, 0, 2, 1, 2), 10L)
                        .sellBlocked(),
                "two affordable uses can");
    }

    // ------------------------------------------------- production wiring arithmetic (R6)

    /**
     * The P0 quantity bug, at the <b>exact</b> arithmetic production performs.
     *
     * <p>The two earlier regressions in this file built the plan by hand and so proved only that the
     * policy compares correctly. Reverting the goal's conversion left both of them green, because the
     * conversion lived in {@code advanceChain} where no JVM test can reach it. This calls the same
     * factory the goal calls.
     */
    @Test
    void mustNotHappen_theChainThresholdIsOpenedFromTheDeficit() {
        TradeChainPlan chain = TradeChainPlan.forDemand(
                MENDING_BOOK, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1, 2, 0L);

        assertEquals(3, chain.targetHeldQuantity(),
                "1 held + 2 needed. Opening on the deficit (2) stops the chain after one purchase");

        assertTrue(TradeChainPolicy.evaluate(chain, new ChainFacts(true, 2, 0, 2, 1, 4), 10L)
                        .active(),
                "and 2 of 3 must still be an active chain");
    }

    /** From an empty backpack the two are equal, which is why every earlier fixture missed it. */
    @Test
    void mustHappen_anEmptyBackpackMakesDeficitAndThresholdAgree() {
        assertEquals(3, TradeChainPlan.forDemand(
                MENDING_BOOK, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 0, 3, 0L)
                .targetHeldQuantity());
    }

    /**
     * The units defect at the production call site: {@code affordableSellUses}, never disposable
     * units.
     */
    @Test
    void mustNotHappen_rawItemUnitsReachTheSellUseComparison() {
        OfferSnapshot sale = OfferSnapshot.of(0, new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(Items.STICK, 32),
                java.util.Optional.empty(), new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
        SellFundingLeg leg = new SellFundingLeg(
                sale, new SellAuthorization(new ItemStack(Items.STICK, 32), 61, MENDING_BOOK), 1, 1);
        TradeFundingPlanner.FundingTarget funding = new TradeFundingPlanner.FundingTarget(
                sale, 2, new TradeEvaluationPolicy.EmeraldDeficit(MENDING_BOOK, 2), leg);

        ChainFacts facts = TradeChainPolicy.factsFrom(funding, 0, 0);

        assertEquals(1, facts.affordableSellUses(),
                "61 disposable sticks at 32 per sale is ONE use - passing 61 here is the R5 defect");
        assertTrue(TradeChainPolicy.evaluate(
                        TradeChainPlan.forDemand(MENDING_BOOK,
                                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 0, 1, 0L),
                        facts, 10L).sellBlocked(),
                "one use cannot fund a two-emerald deficit at one emerald per sale");
    }

    /** No funding target is a chain with no reason to exist. */
    @Test
    void mustHappen_absentFundingTerminatesTheChain() {
        assertFalse(TradeChainPolicy.factsFrom(null, 0, 0).consumerStillWants());
        assertEquals(Termination.CONSUMER_GONE,
                TradeChainPolicy.evaluate(
                                TradeChainPlan.forDemand(MENDING_BOOK,
                                        BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 0, 1, 0L),
                                TradeChainPolicy.factsFrom(null, 0, 0), 10L)
                        .termination());
    }
}
