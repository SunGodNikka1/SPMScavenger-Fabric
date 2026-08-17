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

    /** The TE synthetic quote: logs in, emeralds out. Never a BUY for anything. */
    private static MerchantOffer teSellsLogsForEmeralds() {
        return offer(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.EMERALD, 6));
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
                        offer(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.EMERALD, 64)),
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
                offer(new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.IRON_PICKAXE, 1)),
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
                offer(new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 4)),
                true, Optional.of(charcoal), Optional.empty(),
                backpackWithLogs(), new ScavengerConfig(), List.of(), evidence, SPARE));
    }
}
