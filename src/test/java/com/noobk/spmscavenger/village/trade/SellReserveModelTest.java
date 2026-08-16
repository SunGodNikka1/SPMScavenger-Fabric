package com.noobk.spmscavenger.village.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noobk.spmscavenger.ScavengerConfig;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy.EmeraldDeficit;
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
 * V2-E-R4 — the reserve model that replaced {@code material -> 0}.
 *
 * <p>The defect being pinned is not arithmetic. {@code SellExpendabilityPolicy} was correct
 * throughout and its own tests were green; production simply told it every material had zero
 * reserved units, so it approved everything. <b>A permission policy fed fabricated evidence is a
 * permission policy that is not running</b> — the same shape as the Opinion greet veto and the
 * furnace fuel bug, in a third subsystem.
 */
class SellReserveModelTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_tool_frontier");

    private static SimpleContainer with(ItemStack... stacks) {
        SimpleContainer c = new SimpleContainer(12);
        for (int i = 0; i < stacks.length; i++) {
            c.setItem(i, stacks[i]);
        }
        return c;
    }

    private static OptionalInt reserve(ItemStack material, SimpleContainer backpack) {
        return SellReserveModel.reservedUnits(material, backpack, new ScavengerConfig());
    }

    /**
     * The distinction the whole class exists for: <b>empty is not zero</b>.
     *
     * <p>"Nothing claims this" is a finding. "I do not know what claims this" is ignorance, and
     * ignorance must not authorize spending — the same rule {@code ExistingRouteStatus.UNKNOWN}
     * enforces a layer up.
     */
    @Test
    void mustNotHappen_anUnmodelledMaterialReadsAsFullySpare() {
        SimpleContainer backpack = with(new ItemStack(Items.WHEAT, 64),
                new ItemStack(Items.COAL, 64), new ItemStack(Items.IRON_INGOT, 64));

        for (ItemStack material : List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.CHARCOAL), new ItemStack(Items.DIAMOND))) {
            assertTrue(reserve(material, backpack).isEmpty(),
                    material + " has no reserve model, so it is not authorized funding stock");
        }
    }

    /** The modelled materials answer with a real number, read from the craft chain itself. */
    @Test
    void mustHappen_craftChainMaterialsCarryTheirRealReserve() {
        SimpleContainer backpack = with(new ItemStack(Items.OAK_LOG, 32),
                new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.STICK, 32));

        assertTrue(reserve(new ItemStack(Items.OAK_LOG), backpack).isPresent());
        assertTrue(reserve(new ItemStack(Items.OAK_PLANKS), backpack).isPresent());
        assertEquals(3, reserve(new ItemStack(Items.STICK), backpack).orElseThrow(),
                "the largest live stick claim - campfire (3) over tool (2)");
    }

    /**
     * The end-to-end consequence at the authorization boundary: production refuses wheat today.
     *
     * <p>{@code SellToBuyChainTest} uses wheat with an <i>injected</i> reserve model because the
     * arithmetic is legible there. This is the test that says what the real model does, so the two
     * cannot be confused for each other again.
     */
    @Test
    void mustNotHappen_productionAuthorizesAnUnmodelledMaterial() {
        SimpleContainer backpack = with(new ItemStack(Items.WHEAT, 64));
        OfferSnapshot buysWheat = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.WHEAT, 20), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
        ScavengerConfig cfg = new ScavengerConfig();

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 2), List.of(buysWheat), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY,
                material -> SellReserveModel.reservedUnits(material, backpack, cfg));

        assertNull(leg,
                "64 wheat looks spare and is not: nothing in this mod can say what wants it");
    }

    /**
     * And the positive half, so the refusal above is a model and not a blanket no: a material whose
     * claims <i>are</i> modelled becomes spendable once the chain's reserve is covered.
     */
    @Test
    void mustHappen_surplusBeyondTheChainReserveIsSpendable() {
        SimpleContainer backpack = with(new ItemStack(Items.STICK, 64));
        OfferSnapshot buysSticks = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.STICK, 32), Optional.empty(),
                new ItemStack(Items.EMERALD, 1), 0, 12, 0, 0f));
        ScavengerConfig cfg = new ScavengerConfig();

        SellFundingLeg leg = TradeFundingPlanner.authorizeFunding(
                new EmeraldDeficit(CONSUMER, 1), List.of(buysSticks), backpack,
                ItemStack.EMPTY, ItemStack.EMPTY,
                material -> SellReserveModel.reservedUnits(material, backpack, cfg));

        assertTrue(leg.authorization().permits(buysSticks.costA()),
                "64 sticks less a 3-stick craft claim covers a 32-stick sale");
        assertEquals(61, leg.authorization().disposableUnits());
    }

    /** A reserve is a reserve however attractive the offer: the claim outranks the price. */
    @Test
    void mustNotHappen_aClaimedStackIsSoldBecauseThePriceIsGood() {
        SimpleContainer backpack = with(new ItemStack(Items.STICK, 4));
        OfferSnapshot lucrative = OfferSnapshot.of(0, new MerchantOffer(
                new ItemCost(Items.STICK, 4), Optional.empty(),
                new ItemStack(Items.EMERALD, 64), 0, 12, 0, 0f));
        ScavengerConfig cfg = new ScavengerConfig();

        assertTrue(TradeFundingPlanner.authorizeFunding(
                        new EmeraldDeficit(CONSUMER, 1), List.of(lucrative), backpack,
                        ItemStack.EMPTY, ItemStack.EMPTY,
                        material -> SellReserveModel.reservedUnits(material, backpack, cfg))
                == null, "1 spare stick cannot cover a 4-stick sale, at any price");
    }
}
