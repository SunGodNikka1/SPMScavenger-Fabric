package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.ToolTierPolicy;
import com.noobk.spmscavenger.WorkDemandPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.VillagerTrades;
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
 * V2-H0 static proofs — <b>VR-H0a/b/c</b>, the contracts the VR-T2 fixture will rest on.
 *
 * <p>Each answers a question the fixture would otherwise assume. The runtime proof still belongs to
 * VR-T2; these establish that its premises are true before anyone builds a datapack on them.
 */
class VanillaTradeRouteContractTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------- VR-H0a: tier closure

    /**
     * A component-bearing iron pickaxe still closes {@code iron_pickaxe_upgrade}.
     *
     * <p>Vanilla sells the iron pickaxe only through {@code EnchantedItemForEmeralds}, so the tool
     * the mob actually buys carries components. {@code ToolTierPolicy} evaluates the item, not its
     * components — this pins that rather than reasoning about it, because the entire projection is
     * pointless if the purchased tool cannot satisfy the consumer.
     */
    @Test
    void mustHappen_aComponentBearingIronPickaxeStillClosesTheConsumer() {
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        ItemStack decorated = new ItemStack(Items.IRON_PICKAXE);
        decorated.set(DataComponents.CUSTOM_NAME, Component.literal("Toolsmith's"));
        decorated.setDamageValue(3);

        SimpleContainer withPlain = new SimpleContainer(9);
        withPlain.setItem(0, plain);
        SimpleContainer withDecorated = new SimpleContainer(9);
        withDecorated.setItem(0, decorated);

        assertEquals(ToolTier.IRON,
                ToolTierPolicy.tierOfPick(withPlain, ItemStack.EMPTY, ItemStack.EMPTY));
        assertEquals(ToolTier.IRON,
                ToolTierPolicy.tierOfPick(withDecorated, ItemStack.EMPTY, ItemStack.EMPTY),
                "components do not change tool tier - the purchased tool must close the consumer");

        // And with an iron pick in hand the iron frontier stops asking.
        assertTrue(com.noobk.spmscavenger.ScavengerCrafting
                        .activeIronToolRecipe(withDecorated, ItemStack.EMPTY, ItemStack.EMPTY,
                                new ScavengerConfig())
                        .isEmpty(),
                "obtaining the finished tool makes the source consumer disappear through the "
                        + "existing tier logic - no special close path for a purchased tool");
    }

    /** And the projected BUY is recognised despite the result carrying components. */
    @Test
    void mustHappen_aComponentBearingResultStillMatchesTheProjectedDemand() {
        ItemStack enchantedish = new ItemStack(Items.IRON_PICKAXE);
        enchantedish.set(DataComponents.CUSTOM_NAME, Component.literal("Efficiency I"));

        OfferSnapshot offer = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 14), Optional.empty(), enchantedish, 0, 12, 0, 0f));
        WorkDemandPolicy.MaterialDemand projected = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE), 1,
                com.noobk.spmscavenger.ScavengerCrafting.IRON_PICKAXE_RECIPE.consumerKey());

        assertTrue(TradeEvaluationPolicy.evaluate(projected, offer).viable(),
                "desirability is by registry key; components do not make it a different item");
    }

    // ------------------------------------------------- VR-H0b: component transaction

    /**
     * Snapshot → revalidate → execute preserves a component-bearing result exactly.
     *
     * <p>The strictness is the feature. A quote that rerolls into different components or a different
     * price between selection and arrival is a <b>different purchase</b>, and must be refused rather
     * than silently accepted — snapshot is evidence, not authority.
     *
     * <p><b>What this proves, precisely:</b> component-exact semantics for an <i>arbitrary</i>
     * component payload. It uses {@code CUSTOM_NAME} rather than constructing a real enchanted
     * stack, so it does <b>not</b> pin {@code DataComponents.ENCHANTMENTS} specifically. Production
     * compares through {@code ItemStack.isSameItemSameComponents}, which has no per-component
     * special case, so the generic proof carries — but the actual enchanted vanilla quote is
     * <b>VR-T2's</b> to transact, and this must not be recorded as static proof of enchantments.
     */
    @Test
    void mustHappen_aComponentBearingResultSurvivesTheTransactionExactly() {
        ItemStack sold = new ItemStack(Items.IRON_PICKAXE);
        sold.set(DataComponents.CUSTOM_NAME, Component.literal("Efficiency I"));

        MerchantOffers live = new MerchantOffers();
        live.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 3), Optional.empty(), sold, 0, 12, 0, 0f));
        OfferSnapshot planned = OfferSnapshot.of(0, live.get(0));

        SimpleContainer backpack = new SimpleContainer(9);
        backpack.setItem(0, new ItemStack(Items.EMERALD, 3));

        assertEquals(VillagerTradeAdapter.TradeResult.TRADED,
                VillagerTradeAdapter.executeAgainst(backpack, live, planned, o -> { }));

        ItemStack received = null;
        for (int slot = 0; slot < backpack.getContainerSize(); slot++) {
            if (backpack.getItem(slot).is(Items.IRON_PICKAXE)) {
                received = backpack.getItem(slot);
            }
        }
        assertTrue(received != null && ItemStack.isSameItemSameComponents(received, sold),
                "the enchantment components must reach the backpack, not a normalised plain tool");
    }

    /** A rerolled quote is a different purchase, and revalidation must say so. */
    @Test
    void mustNotHappen_aRerolledQuoteIsAcceptedAsThePlannedOne() {
        ItemStack efficiency = new ItemStack(Items.IRON_PICKAXE);
        efficiency.set(DataComponents.CUSTOM_NAME, Component.literal("Efficiency I"));
        ItemStack unbreaking = new ItemStack(Items.IRON_PICKAXE);
        unbreaking.set(DataComponents.CUSTOM_NAME, Component.literal("Unbreaking I"));

        OfferSnapshot planned = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.EMERALD, 7), Optional.empty(), efficiency, 0, 12, 0, 0f));

        assertFalse(planned.matchesLive(new MerchantOffer(
                        new ItemCost(Items.EMERALD, 7), Optional.empty(), unbreaking, 0, 12, 0, 0f)),
                "same item, different components - a different tool than the one agreed");
        assertFalse(planned.matchesLive(new MerchantOffer(
                        new ItemCost(Items.EMERALD, 9), Optional.empty(), efficiency, 0, 12, 0, 0f)),
                "same tool, different price - a price the mob never agreed to");
        assertTrue(planned.matchesLive(new MerchantOffer(
                new ItemCost(Items.EMERALD, 7), Optional.empty(), efficiency, 0, 12, 0, 0f)));
    }

    // ------------------------------------------------- VR-H0c: the vanilla route premise

    private record Listing(String profession, int level, VillagerTrades.ItemListing listing) {
    }

    private static List<Listing> allListings() {
        List<Listing> all = new ArrayList<>();
        VillagerTrades.TRADES.forEach((profession, byLevel) -> byLevel.forEach((level, listings) -> {
            for (VillagerTrades.ItemListing listing : listings) {
                all.add(new Listing(profession.name(), level, listing));
            }
        }));
        return all;
    }

    private static Object field(Object owner, String name) {
        try {
            java.lang.reflect.Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * The exact funding route V2-H depends on — asserted, not merely discovered.
     *
     * <p>The earlier probe only checked that <i>some</i> reserve-modelled material had a vanilla
     * buyer while the prose claimed Fletcher/32 sticks. This pins the whole discovered set, so the
     * narrower claim is the one under test: if vanilla ever adds another buyer of a modelled
     * material, this fails and the fixture's premise is re-examined deliberately.
     */
    @Test
    void mustHappen_theFletcherStickRouteIsTheVanillaFundingPremise() {
        List<String> routes = new ArrayList<>();
        for (Listing entry : allListings()) {
            if (!(field(entry.listing(), "itemStack") instanceof ItemCost cost)) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(cost.item().value()).toString();
            if (!id.equals("minecraft:stick") && !id.endsWith("_log") && !id.endsWith("_planks")) {
                continue;
            }
            Object emeralds = field(entry.listing(), "emeraldAmount");
            routes.add(entry.profession() + " lvl" + entry.level() + ": " + cost.count() + "x "
                    + id + " -> " + emeralds + " emerald");
        }

        assertEquals(List.of("fletcher lvl1: 32x minecraft:stick -> 1 emerald"), routes,
                "the complete set of vanilla buyers for a SellReserveModel-authorized material. "
                        + "V2-H's funding leg is this one route and no other.");
    }

    /**
     * The Fletcher must have enough uses for the fixture's four sales.
     *
     * <p>More important than backpack capacity, and for a reason arithmetic alone would miss:
     * {@code TradeFundingPlanner} caps affordable uses by {@code maxUses - uses}, so a backpack full
     * of sticks cannot make a villager accept a fifth trade once its offer is exhausted. A fixture
     * that needs K sales against an offer allowing fewer would stall with {@code sellBlocked} and
     * look like a trade defect.
     *
     * <p>Extracted from the same real listing instrument rather than remembered.
     */
    @Test
    void mustHappen_theFletcherStickOfferAllowsTheFixturesFourSales() {
        Integer maxUses = null;
        for (Listing entry : allListings()) {
            if (field(entry.listing(), "itemStack") instanceof ItemCost cost
                    && cost.item().value() == Items.STICK) {
                maxUses = (Integer) field(entry.listing(), "maxUses");
            }
        }

        assertTrue(maxUses != null, "the stick listing must still exist");
        System.out.println("[VR-H0c] fletcher stick offer maxUses=" + maxUses
                + " (fresh offer uses=0, so remaining=" + maxUses + ")");
        assertTrue(maxUses >= 4,
                "the fixture performs four SELLs; a fresh offer must allow them. Observed: "
                        + maxUses);
    }

    /**
     * The Toolsmith's iron-pickaxe price <b>envelope</b>, so the fixture never hardcodes a price.
     *
     * <p>{@code EnchantedItemForEmeralds} rolls {@code level = 5 + nextInt(15)} and charges
     * {@code min(baseEmeraldCost + level, 64)}. The fixture must therefore be built against the
     * range, and its PASS criterion must assert relationships rather than a number — production
     * already derives funding from the live quote, so a fixed-price fixture would test a weaker
     * system than the one shipping.
     */
    @Test
    void mustHappen_theIronPickaxePriceEnvelopeIsKnown() {
        Integer base = null;
        for (Listing entry : allListings()) {
            if (!entry.listing().getClass().getSimpleName().equals("EnchantedItemForEmeralds")) {
                continue;
            }
            if (field(entry.listing(), "itemStack") instanceof ItemStack stack
                    && stack.is(Items.IRON_PICKAXE)) {
                base = (Integer) field(entry.listing(), "baseEmeraldCost");
            }
        }

        assertTrue(base != null, "vanilla must still sell an iron pickaxe for the projection to serve");
        int min = Math.min(base + 5, 64);
        int max = Math.min(base + 19, 64);

        System.out.println("[VR-H0c] iron_pickaxe baseEmeraldCost=" + base
                + " envelope=[" + min + ".." + max + "] emeralds"
                + " | funding: 32 sticks -> 1 emerald"
                + " | sells needed at max = " + max
                + " | sticks needed at max = " + (max * 32));

        assertTrue(min >= 1 && max <= 64, "the vanilla clamp bounds the envelope");
        assertTrue(max > min, "the price genuinely varies - a fixed-price fixture would be a lie");
    }
}
