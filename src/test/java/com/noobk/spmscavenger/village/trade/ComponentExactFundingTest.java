package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.WorkDemandPolicy;
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

/**
 * D-VR-077 step 5.5 — <b>category reserves, exact payment.</b>
 *
 * <h2>The representation mismatch</h2>
 *
 * {@code TradeTransaction.debit} spends by {@code isSameItemSameComponents}, and
 * {@link OfferRef.Requote} identifies a quote the same way — components decide the price, so they
 * decide what pays it. But planning counted by item alone, so:
 *
 * <pre>
 * 63x oak_log + 1x oak_log{damaged}      reserve 3
 * quote: 22x oak_log{damaged} -&gt; 1 emerald
 *
 * planning   held 64, disposable 61   -&gt; fundable
 * truth      1 exact unit             -&gt; CANNOT_AFFORD
 * </pre>
 *
 * The exact debit means nothing is stolen — but the mob commits to a route it cannot execute, walks
 * to the merchant, fails, and may pass over one that would have worked. Vanilla boards rarely carry
 * component-bearing costs; a re-quoting source makes them ordinary, which is why this is repaired
 * before step 6 rather than after the first confusing runtime report.
 *
 * <p>{@code damaged} stands in for any component difference. The rule is about components, not
 * durability.
 */
class ComponentExactFundingTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Everything modelled and spare, so the reserve never masks the exactness question. */
    private static final java.util.function.Function<ItemStack, OptionalInt> SPARE =
            stack -> OptionalInt.of(0);

    private static ItemStack variant(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("variant"));
        return stack;
    }

    private static OfferSnapshot sellFor(ItemStack cost, int emeralds, int rankOrdinal) {
        return OfferSnapshot.of(rankOrdinal, new MerchantOffer(
                new ItemCost(cost.getItemHolder(), cost.getCount(),
                        net.minecraft.core.component.DataComponentPredicate
                                .allOf(cost.getComponents())),
                Optional.empty(), new ItemStack(Items.EMERALD, emeralds), 0, 16, 0, 0f));
    }

    private static OfferSnapshot buy(int emeralds, int rankOrdinal) {
        return OfferSnapshot.of(rankOrdinal, new MerchantOffer(
                new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));
    }

    private static WorkDemandPolicy.MaterialDemand ironDemand() {
        return new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 1,
                ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade"));
    }

    // ------------------------------------------------------------------ the fact

    @Test
    void mustHappen_exactCountingSeesOnlyTheMatchingVariant() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 63));
        backpack.setItem(1, variant(Items.OAK_LOG, 1));

        assertEquals(1, TradeInventoryFacts.countExact(backpack, variant(Items.OAK_LOG, 1)),
                "one component-bearing log, whatever the pile beside it holds");
        assertEquals(63, TradeInventoryFacts.countExact(backpack, new ItemStack(Items.OAK_LOG)),
                "and the ordinary variant does not borrow the other's units either");
    }

    @Test
    void mustHappen_affordableUsesTakeTheSmallerOfCategoryAndExact() {
        assertEquals(0, TradeInventoryFacts.affordableExactUses(61, 1, 22),
                "61 spare logs, one of the right variant, 22 per use");
        assertEquals(2, TradeInventoryFacts.affordableExactUses(61, 44, 22),
                "44 exact units is two uses, and the category allows it");
        assertEquals(1, TradeInventoryFacts.affordableExactUses(30, 64, 22),
                "the category reserve binds when it is the tighter of the two");
    }

    // ------------------------------------------------------------------ the funding route

    /** The concrete failure: a route that planning called fundable and the debit would refuse. */
    @Test
    void mustNotHappen_aVariantSaleIsFundedFromOrdinaryStock() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 63));
        backpack.setItem(1, variant(Items.OAK_LOG, 1));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buy(4, 0), sellFor(variant(Items.OAK_LOG, 22), 1, 1)),
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, SPARE);

        assertNull(target.sellLeg(),
                "one exact unit cannot fund a 22-unit sale, however large the ordinary pile is");
    }

    /** And the same route works once the exact variant is genuinely held. */
    @Test
    void mustHappen_aVariantSaleIsFundedFromMatchingStock() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 20));
        backpack.setItem(1, variant(Items.OAK_LOG, 44));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buy(2, 0), sellFor(variant(Items.OAK_LOG, 22), 1, 1)),
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, SPARE);

        assertNotNull(target.sellLeg(), "44 exact units is two uses");
        assertTrue(target.actionable(), "and two emeralds close a two-emerald deficit");
    }

    /** Two variants of one item are not interchangeable in either direction. */
    @Test
    void mustNotHappen_theOrdinaryVariantIsFundedFromVariantStock() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, variant(Items.OAK_LOG, 64));
        backpack.setItem(1, new ItemStack(Items.OAK_LOG, 5));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buy(4, 0), sellFor(new ItemStack(Items.OAK_LOG, 22), 1, 1)),
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, SPARE);

        assertNull(target.sellLeg(), "exactness is symmetric - neither pile pays for the other");
    }

    /** Ordinary component-free materials must behave exactly as before. */
    @Test
    void mustHappen_componentFreeMaterialsKeepTodaysBehaviour() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));

        TradeFundingPlanner.FundingTarget target = TradeFundingPlanner.chooseFundingTarget(
                ironDemand(), List.of(buy(2, 0), sellFor(new ItemStack(Items.OAK_LOG, 22), 1, 1)),
                backpack, ItemStack.EMPTY, ItemStack.EMPTY, SPARE);

        assertNotNull(target.sellLeg(), "the ordinary path is untouched");
        assertEquals(2, target.sellLeg().affordableUses(), "64 / 22 = 2 uses");
    }

    // ------------------------------------------------------------------ the payment check

    @Test
    void mustNotHappen_nonEmeraldPaymentIsAffordableFromTheWrongVariant() {
        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.OAK_LOG, 64));

        OfferSnapshot compound = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 2),
                Optional.of(new ItemCost(variant(Items.OAK_LOG, 4).getItemHolder(), 4,
                        net.minecraft.core.component.DataComponentPredicate
                                .allOf(variant(Items.OAK_LOG, 4).getComponents()))),
                new ItemStack(Items.IRON_INGOT, 1), 0, 12, 0, 0f));

        assertFalse(VillagerTradeAdapter.canAffordNonEmerald(backpack, compound),
                "64 ordinary logs do not pay a cost that names a component variant");

        backpack.setItem(1, variant(Items.OAK_LOG, 4));
        assertTrue(VillagerTradeAdapter.canAffordNonEmerald(backpack, compound),
                "and four of the right variant do");
    }
}
