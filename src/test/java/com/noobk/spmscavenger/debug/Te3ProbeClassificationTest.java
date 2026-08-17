package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
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

/**
 * <b>TEMPORARY V2-TE P0-3 PROBE SUPPORT — remove with the probe.</b>
 *
 * <p>Tests the probe's <b>market enumeration</b>, not {@code TradeFundingPlanner}. The planner was
 * correct through every revision of this probe; the defect was always caller scope — first a single
 * offer, then one merchant's board — and a test that exercised the planner would have proved the
 * component that was never broken.
 *
 * <p>So the fixture below is built so that <b>merchant A has no qualifying BUY at all</b>. Any
 * regression to same-merchant search turns the positive case red rather than merely making it less
 * thorough.
 */
class Te3ProbeClassificationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation IRON_TOOL =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");

    /** Everything modelled and spare, so disposition never masks a classification result. */
    private static final java.util.function.Function<ItemStack, java.util.OptionalInt> SPARE =
            stack -> java.util.OptionalInt.of(0);

    private static MerchantOffer offer(ItemStack costA, ItemStack result) {
        return new MerchantOffer(new ItemCost(costA.getItem(), costA.getCount()),
                Optional.empty(), result, 0, 12, 0, 0f);
    }

    /**
     * A <b>TE synthetic</b> offer. {@code maxUses} is not decoration:
     * {@code SyntheticOfferFactory.MAX_USES = 999_999} (verified at pinned commit {@code fe305e6}),
     * and {@code SellFundingLeg.affordableUses} is {@code min(inventoryUses, maxUses - uses)}. Minting
     * a TE quote with vanilla's 12 silently caps a 17-use plan at 12 and turns a real witness into
     * {@code C_IRRELEVANT} - which is exactly what this helper's absence did on the first run.
     */
    private static MerchantOffer teQuote(ItemStack costA, ItemStack result) {
        return new MerchantOffer(new ItemCost(costA.getItem(), costA.getCount()),
                Optional.empty(), result, 0, 999_999, 0, 0f);
    }

    /** The TE synthetic quote: logs in, emeralds out. Never a BUY for anything. */
    private static MerchantOffer teSellsLogsForEmeralds() {
        return teQuote(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.EMERALD, 6));
    }

    private static WorkDemandPolicy.MaterialDemand pickaxeDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE), 1, IRON_TOOL);
    }

    private static SimpleContainer backpackWithLogs() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        return backpack;
    }

    /**
     * The R9 defect, as behaviour.
     *
     * <pre>
     * merchant A (fletcher)  TE quote: 16 logs -&gt; 6 emerald     and NO qualifying BUY
     * merchant B (toolsmith) vanilla:   5 emerald -&gt; iron_pickaxe
     * </pre>
     *
     * V2-E R7 supports a SELL on one merchant funding a BUY on another, and VR-T2 proved it
     * physically. Searching only the selling merchant's own board for that BUY reported exactly this
     * route as {@code C_IRRELEVANT}.
     */
    @Test
    void mustHappen_aTeSellOnOneMerchantFundsABuyOnAnother() {
        SimpleContainer backpack = backpackWithLogs();
        String[] evidence = new String[1];

        Te3ProbeCommand.Bucket bucket = Te3ProbeCommand.classify(
                teSellsLogsForEmeralds(), true,
                Optional.of(pickaxeDemand()), Optional.empty(),
                backpack, new ScavengerConfig(),
                List.of(
                        // A: sells nothing this consumer wants - it only BUYS wheat.
                        new Te3ProbeCommand.MarketBoard("fletcher", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.WHEAT, 20),
                                        new ItemStack(Items.EMERALD, 1))))),
                        // B: the qualifying purchase, on a DIFFERENT merchant.
                        new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 5),
                                        new ItemStack(Items.IRON_PICKAXE, 1)))))),
                evidence, SPARE);

        assertEquals(Te3ProbeCommand.Bucket.B_FUNDING, bucket,
                "the TE emerald payout funds a real purchase on another villager - reverting to "
                        + "same-merchant search makes this fail, which is the point of the fixture");
        assertNotNull(evidence[0], "B must name the purchase it funds");
        org.junit.jupiter.api.Assertions.assertTrue(evidence[0].contains("toolsmith"),
                "and name the merchant it is on: " + evidence[0]);
    }

    /** Same TE quote, no useful purchase anywhere in the market. */
    @Test
    void mustHappen_aTeSellWithNoUsefulBuyAnywhereIsIrrelevant() {
        String[] evidence = new String[1];

        Te3ProbeCommand.Bucket bucket = Te3ProbeCommand.classify(
                teSellsLogsForEmeralds(), true,
                Optional.of(pickaxeDemand()), Optional.empty(),
                backpackWithLogs(), new ScavengerConfig(),
                List.of(
                        new Te3ProbeCommand.MarketBoard("fletcher", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.WHEAT, 20),
                                        new ItemStack(Items.EMERALD, 1))))),
                        new Te3ProbeCommand.MarketBoard("farmer", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 1),
                                        new ItemStack(Items.BREAD, 6)))))),
                evidence, SPARE);

        assertEquals(Te3ProbeCommand.Bucket.C_IRRELEVANT, bucket,
                "emeralds nobody can spend on the current demand are not a funding route");
        assertNull(evidence[0], "C must not fabricate funding evidence");
    }

    /** Disposition outranks value: an unauthorized input is D however lucrative the quote. */
    @Test
    void mustNotHappen_aLucrativeQuoteOnAnUnauthorizedInputIsScoredOnValue() {
        String[] evidence = new String[1];

        assertEquals(Te3ProbeCommand.Bucket.D_ILLEGAL, Te3ProbeCommand.classify(
                        teQuote(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.EMERALD, 64)),
                        false,
                        Optional.of(pickaxeDemand()), Optional.empty(),
                        backpackWithLogs(), new ScavengerConfig(),
                        List.of(new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 5),
                                        new ItemStack(Items.IRON_PICKAXE, 1)))))),
                        evidence, SPARE),
                "W-5: disposition runs before valuation");
    }

    /** A payout that IS the demanded item is A, and never reaches the funding search. */
    @Test
    void mustHappen_aPayoutMatchingTheDemandIsDirect() {
        String[] evidence = new String[1];

        assertEquals(Te3ProbeCommand.Bucket.A_DIRECT, Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.IRON_PICKAXE, 1)),
                true, Optional.of(pickaxeDemand()), Optional.empty(),
                backpackWithLogs(), new ScavengerConfig(), List.of(), evidence, SPARE));
    }

    /**
     * The coal/charcoal case: the payout would serve the torch chain, but the demand names
     * charcoal. `E`, never `C` — the difference decides whether the fix is compatibility code or a
     * demand-representation repair.
     */
    @Test
    void mustHappen_coalAgainstACharcoalDemandIsARepresentationMiss() {
        String[] evidence = new String[1];
        WorkDemandPolicy.MaterialDemand charcoal = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 2,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "torch_chain"));

        assertEquals(Te3ProbeCommand.Bucket.E_REPRESENTATION_MISS, Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 4)),
                true, Optional.of(charcoal), Optional.empty(),
                backpackWithLogs(), new ScavengerConfig(), List.of(), evidence, SPARE));
    }

    /**
     * R11 — the runtime false positive. <b>Quotable is not fundable.</b>
     *
     * <p>Run #2 produced {@code 22 oak_log -> 1 emerald} against a {@code 13 emerald} purchase with
     * 48 logs held: {@code floor(48/22) = 2} affordable uses yielding 2 emeralds against a deficit
     * of 13. A planner target exists, so the earlier check passed — but the purchase can never
     * complete, and calling that a funding route would have reported V2-TE as reachable on the
     * strength of trades the mob could not finish.
     */
    @Test
    void mustNotHappen_aPartiallyFundingTeQuoteCountsAsAFundingRoute() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 48));
        String[] evidence = new String[1];

        Te3ProbeCommand.Bucket bucket = Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1)),
                true, Optional.of(pickaxeDemand()), Optional.empty(),
                backpack, new ScavengerConfig(),
                List.of(new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                        OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 13),
                                new ItemStack(Items.IRON_PICKAXE, 1)))))),
                evidence, SPARE);

        assertEquals(Te3ProbeCommand.Bucket.C_IRRELEVANT, bucket,
                "2 affordable uses x 1 emerald cannot close a 13-emerald deficit");
        assertNull(evidence[0], "and a route that cannot complete must not be evidenced as B");
    }

    // ------------------------------------------------------------------ R12 B witness

    /** The exact fixture the b_funding_witness scenario seeds, as a pure container. */
    private static SimpleContainer fundingFixture() {
        SimpleContainer backpack = new SimpleContainer(Te3ProbeCommand.FUNDING_SLOTS);
        Te3ProbeCommand.seedFundingInventory(backpack);
        return backpack;
    }

    /**
     * R12 — <b>value is not capacity.</b>
     *
     * <p>The census recommended "&ge;484 logs", which is arithmetically right and physically wrong:
     * 8 slots of logs leaves the first {@code 22 oak_log -> 1 emerald} nowhere to put its emerald,
     * and {@code VillagerTradeAdapter} requires the result to insert before it commits. The
     * seventeen-use plan would die on use one.
     */
    @Test
    void mustHappen_theFundingFixtureLeavesSomewhereForTheTePayoutToLand() {
        SimpleContainer backpack = fundingFixture();

        assertEquals(384, com.noobk.spmscavenger.ScavengerCrafting.count(backpack, Items.OAK_LOG),
                "6 stacks of logs - value enough for the whole 8..22 envelope at 22 logs/emerald");
        org.junit.jupiter.api.Assertions.assertTrue(
                Te3ProbeCommand.hasEmeraldMergeRoom(backpack),
                "the TE payout must have an existing emerald stack to merge into");
        assertEquals(0, Te3ProbeCommand.freeSlots(backpack),
                "and it must not need a free slot to do it - the fixture is deliberately full, so "
                        + "this passes because of the merge target, not because of spare room");
    }

    /**
     * The capacity negative control, and the documented limit it exposes.
     *
     * <p>An all-logs backpack is <i>worth more</i> and classifies <b>identically</b> as
     * {@code B_FUNDING} — because {@code classify} asks whether the route can be assembled, and
     * insertion headroom is not part of that question. Writing a second transaction model inside the
     * probe to close the gap would have produced an oracle agreeing with itself, so the gap is
     * reported instead: {@code freeSlots == 0 && !hasEmeraldMergeRoom} is the signal, and execution
     * capacity remains <b>P0-2</b>.
     */
    @Test
    void mustNotHappen_aBFundingResultIsReadAsPhysicallyExecutable() {
        SimpleContainer allLogs = new SimpleContainer(Te3ProbeCommand.FUNDING_SLOTS);
        for (int slot = 0; slot < Te3ProbeCommand.FUNDING_SLOTS; slot++) {
            allLogs.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        var toolsmith = List.of(new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 22),
                        new ItemStack(Items.IRON_PICKAXE, 1))))));

        assertEquals(Te3ProbeCommand.Bucket.B_FUNDING, Te3ProbeCommand.classify(
                        teQuote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1)),
                        true, Optional.of(pickaxeDemand()), Optional.empty(),
                        allLogs, new ScavengerConfig(), toolsmith, new String[1], SPARE),
                "classification sees only assembly - 512 logs fund 22 emeralds");
        org.junit.jupiter.api.Assertions.assertFalse(
                Te3ProbeCommand.hasEmeraldMergeRoom(allLogs), "no emerald stack to merge into");
        assertEquals(0, Te3ProbeCommand.freeSlots(allLogs), "and no free slot either");
        // Both conditions together are what the scan prints a CAPACITY WARNING for. The fixture
        // above must never satisfy them.
        org.junit.jupiter.api.Assertions.assertTrue(
                Te3ProbeCommand.hasEmeraldMergeRoom(fundingFixture()),
                "the shipped fixture must not be this case");
    }

    /**
     * R12 — the witness itself, at the <b>worst</b> price in the confirmed envelope.
     *
     * <p>22 emeralds is the ceiling of the iron-pickaxe range, so if this funds, every legal roll
     * funds: 5 held + {@code floor(383/22) = 17} TE uses = exactly 22. The armorer board carries no
     * qualifying BUY, so a regression to same-merchant search turns this red rather than merely
     * making it less thorough.
     */
    @Test
    void mustHappen_theWitnessFundsTheWorstPriceInTheEnvelopeAcrossVillagers() {
        String[] evidence = new String[1];

        Te3ProbeCommand.Bucket bucket = Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1)),
                true, Optional.of(pickaxeDemand()), Optional.empty(),
                fundingFixture(), new ScavengerConfig(),
                List.of(
                        // The TE seller's own board: two vanilla ItemsForEmeralds armour listings,
                        // which is exactly why DefaultBuyItemSelector fell back to EMERALD. Nothing
                        // here can fund an iron pickaxe.
                        new Te3ProbeCommand.MarketBoard("armorer", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 4),
                                        new ItemStack(Items.IRON_BOOTS, 1))),
                                OfferSnapshot.of(1, offer(new ItemStack(Items.EMERALD, 5),
                                        new ItemStack(Items.IRON_HELMET, 1))))),
                        new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 22),
                                        new ItemStack(Items.IRON_PICKAXE, 1)))))),
                evidence, SPARE);

        assertEquals(Te3ProbeCommand.Bucket.B_FUNDING, bucket,
                "5 held + 17 TE uses = 22 emeralds against the ceiling price");
        assertNotNull(evidence[0], "B must name the purchase it funds");
        org.junit.jupiter.api.Assertions.assertTrue(evidence[0].contains("toolsmith"),
                "and the BUY owner must be the OTHER villager: " + evidence[0]);
    }

    /** One emerald short of the ceiling is not a witness. Stock, not structure, is the difference. */
    @Test
    void mustNotHappen_aFixtureOneEmeraldShortStillReportsAWitness() {
        SimpleContainer thin = new SimpleContainer(Te3ProbeCommand.FUNDING_SLOTS);
        Te3ProbeCommand.seedFundingInventory(thin);
        // 361 logs -> floor(361/22) = 16 uses; 5 held + 16 = 21 against a 22-emerald pickaxe.
        thin.setItem(5, new ItemStack(Items.OAK_LOG, 41));

        assertEquals(Te3ProbeCommand.Bucket.C_IRRELEVANT, Te3ProbeCommand.classify(
                        teQuote(new ItemStack(Items.OAK_LOG, 22), new ItemStack(Items.EMERALD, 1)),
                        true, Optional.of(pickaxeDemand()), Optional.empty(),
                        thin, new ScavengerConfig(),
                        List.of(new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 22),
                                        new ItemStack(Items.IRON_PICKAXE, 1)))))),
                        new String[1], SPARE),
                "21 of 22 emeralds is a purchase that never completes");
    }

    /** The boundary: enough stock to fully fund is B, one use short is not. */
    @Test
    void mustHappen_theFundingBoundaryIsExact() {
        String[] evidence = new String[1];
        var buy = List.of(new Te3ProbeCommand.MarketBoard("toolsmith", List.of(
                OfferSnapshot.of(0, offer(new ItemStack(Items.EMERALD, 4),
                        new ItemStack(Items.IRON_PICKAXE, 1))))));

        SimpleContainer enough = new SimpleContainer(9);
        enough.setItem(0, new ItemStack(Items.OAK_LOG, 40));   // floor(40/10)=4 uses x1 = 4 >= 4
        assertEquals(Te3ProbeCommand.Bucket.B_FUNDING, Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.EMERALD, 1)),
                true, Optional.of(pickaxeDemand()), Optional.empty(),
                enough, new ScavengerConfig(), buy, evidence, SPARE));

        SimpleContainer oneShort = new SimpleContainer(9);
        oneShort.setItem(0, new ItemStack(Items.OAK_LOG, 39));  // floor(39/10)=3 uses x1 = 3 < 4
        assertEquals(Te3ProbeCommand.Bucket.C_IRRELEVANT, Te3ProbeCommand.classify(
                teQuote(new ItemStack(Items.OAK_LOG, 10), new ItemStack(Items.EMERALD, 1)),
                true, Optional.of(pickaxeDemand()), Optional.empty(),
                oneShort, new ScavengerConfig(), buy, new String[1], SPARE));
    }
}
