package com.noobk.spmscavenger.debug;

import com.noobk.spmscavenger.ToolTier;
import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.compat.tradeeverything.TradeEverythingCurrencyProvider;
import com.noobk.spmscavenger.village.trade.OfferRef;
import com.noobk.spmscavenger.village.trade.OfferSnapshot;
import com.noobk.spmscavenger.village.trade.MerchantCurrencyPolicies;
import com.noobk.spmscavenger.village.trade.SellAuthorization;
import com.noobk.spmscavenger.village.trade.SellFundingLeg;
import com.noobk.spmscavenger.village.trade.TradeEvaluationPolicy;
import com.noobk.spmscavenger.village.trade.TradeFundingPlanner;
import com.noobk.spmscavenger.village.trade.TradeSourceKey;
import com.noobk.spmscavenger.village.trade.TradeTransaction;
import com.noobk.spmscavenger.village.trade.VanillaMerchantCurrency;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Temporary V2-TE witness state-machine and evidence-fidelity tests. */
class TeCurrencyWitnessTrackerTest {

    private static final TradeEverythingCurrencyProvider TE = new TradeEverythingCurrencyProvider();
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @AfterEach
    void resetWitness() {
        TeCurrencyWitnessTracker.reset();
        MerchantCurrencyPolicies.installOptionalProvider(VanillaMerchantCurrency.INSTANCE);
    }

    @BeforeEach
    void installTestCurrencyPolicy() {
        MerchantCurrencyPolicies.installOptionalProvider(TE);
    }

    @Test
    void mustHappen_fullPassiveEvidenceChainProvesInvisibleStagingAndExactQ2Identity() {
        UUID mobId = UUID.randomUUID();
        ItemStack witness = witnessSticks(4);
        SimpleContainer backpack = backpack(witness);
        arm(mobId, backpack);

        WorkDemandPolicy.MaterialDemand demand = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3,
                TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER);
        TeCurrencyWitnessTracker.observeDemand(mobId, backpack, demand, 101);

        MerchantOffer q1Live = offer(witness.copyWithCount(1),
                new ItemStack(Items.EMERALD_BLOCK, 10));
        OfferSnapshot q1 = snapshot(new OfferRef.Requote(witness), 1, q1Live);
        TeCurrencyWitnessTracker.observeQ1(
                mobId, backpack, TradeSourceKey.TRADE_EVERYTHING, q1, 102);

        MerchantOffer purchaseLive = buy(10);
        OfferSnapshot purchase = snapshot(OfferRef.board(0), 0, purchaseLive);
        TradeEvaluationPolicy.EmeraldDeficit deficit =
                new TradeEvaluationPolicy.EmeraldDeficit(
                        TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER, 10);
        SellFundingLeg leg = new SellFundingLeg(
                q1,
                new SellAuthorization(witness.copyWithCount(1), 1,
                        TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER),
                90,
                1);
        TradeFundingPlanner.FundingTarget funding =
                new TradeFundingPlanner.FundingTarget(purchase, 10, deficit, leg);
        TeCurrencyWitnessTracker.observeFundingPlan(
                mobId, backpack, TradeSourceKey.TRADE_EVERYTHING, funding, leg, 103);

        // Q2 is a distinct object with the same strict semantics; that exact object must reach the
        // executor and notifier unchanged.
        MerchantOffer q2 = offer(witness.copyWithCount(1),
                new ItemStack(Items.EMERALD_BLOCK, 10));
        TeCurrencyWitnessTracker.observeQ2(
                mobId, backpack, TradeSourceKey.TRADE_EVERYTHING, q1, q2, 104);
        TeCurrencyWitnessTracker.observeExecutionOffer(backpack, q2, 105);
        transact(backpack, q2, 105);

        assertEquals(3, countWitnessSticks(backpack));
        assertEquals(10, backpack.countItem(Items.EMERALD_BLOCK));
        assertEquals(0, backpack.countItem(Items.EMERALD));

        TeCurrencyWitnessTracker.observePurchaseSelected(mobId, backpack, purchase, 106);
        transact(backpack, purchaseLive, 107);

        assertEquals(TeCurrencyWitnessTracker.State.PASS,
                TeCurrencyWitnessTracker.stateForTests());
        assertFalse(TeCurrencyWitnessTracker.retainsLiveReferencesForTests(),
                "PASS must reduce container/Q2 identity to immutable report evidence");
        assertEquals(8, backpack.countItem(Items.EMERALD_BLOCK));
        assertEquals(8, backpack.countItem(Items.EMERALD));
        assertEquals(1, backpack.countItem(Items.IRON_PICKAXE));

        List<String> report = TeCurrencyWitnessTracker.reportLines();
        assertTrue(report.contains("Q1/Q2 semantic correspondence: PASS"));
        assertTrue(report.contains("Exact Q2 object passed to executor: PASS"));
        assertTrue(report.contains("Minimum block conversion: PASS"));
        assertTrue(report.contains("Real inventory unchanged during staging: PASS"));
        assertTrue(report.contains("Exact payment debit: PASS"));
        assertTrue(report.contains("Correct loose change: PASS"));
        assertTrue(report.contains("VERDICT: PASS"));
    }

    @Test
    void mustNotHappen_semanticallyEqualReplacementPassesTheQ2IdentityGate() {
        UUID mobId = UUID.randomUUID();
        ItemStack witness = witnessSticks(4);
        SimpleContainer backpack = backpack(witness);
        arm(mobId, backpack);
        MerchantOffer q2 = offer(witness.copyWithCount(1),
                new ItemStack(Items.EMERALD_BLOCK, 10));
        OfferSnapshot planned = snapshot(new OfferRef.Requote(witness), 0, q2);
        TeCurrencyWitnessTracker.observeQ2(
                mobId, backpack, TradeSourceKey.TRADE_EVERYTHING, planned, q2, 10);

        MerchantOffer rebuilt = offer(witness.copyWithCount(1),
                new ItemStack(Items.EMERALD_BLOCK, 10));
        TeCurrencyWitnessTracker.observeExecutionOffer(backpack, rebuilt, 11);

        assertEquals(TeCurrencyWitnessTracker.State.FAIL,
                TeCurrencyWitnessTracker.stateForTests());
        assertFalse(TeCurrencyWitnessTracker.retainsLiveReferencesForTests());
        assertTrue(TeCurrencyWitnessTracker.reportLines().stream()
                .anyMatch(line -> line.contains("different object")));
    }

    @Test
    void mustNotHappen_wrongUuidOrContainerAdvancesTheArmedSession() {
        UUID mobId = UUID.randomUUID();
        SimpleContainer backpack = backpack(witnessSticks(4));
        arm(mobId, backpack);
        WorkDemandPolicy.MaterialDemand demand = new WorkDemandPolicy.MaterialDemand(
                BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3,
                TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER);

        TeCurrencyWitnessTracker.observeDemand(UUID.randomUUID(), backpack, demand, 1);
        TeCurrencyWitnessTracker.observeDemand(mobId, new SimpleContainer(8), demand, 2);

        assertEquals(TeCurrencyWitnessTracker.State.ARMED,
                TeCurrencyWitnessTracker.stateForTests());
    }

    @Test
    void mustHappen_secondArmIsRefusedAndLifecycleAbortReleasesReferences() {
        UUID first = UUID.randomUUID();
        SimpleContainer backpack = backpack(witnessSticks(4));
        arm(first, backpack);

        UUID second = UUID.randomUUID();
        SimpleContainer secondPack = backpack(witnessSticks(4));
        TeCurrencyWitnessTracker.ArmResult refused =
                TeCurrencyWitnessTracker.armForTest(validPreflight(second, secondPack), secondPack, 2);
        assertFalse(refused.armed());
        assertTrue(refused.lines().stream().anyMatch(line -> line.contains("already armed")));

        TeCurrencyWitnessTracker.abortForMob(first, "PlayerMob unloaded", 3);
        assertEquals(TeCurrencyWitnessTracker.State.INCOMPLETE,
                TeCurrencyWitnessTracker.stateForTests());
        assertFalse(TeCurrencyWitnessTracker.retainsLiveReferencesForTests());
    }

    @Test
    void mustHappen_preflightReportsCurrencyAndQuoteAsIndependentFacts() {
        UUID mobId = UUID.randomUUID();
        SimpleContainer backpack = backpack(witnessSticks(4));
        TeCurrencyWitnessTracker.Preflight unhealthyQuote = new TeCurrencyWitnessTracker.Preflight(
                mobId, "Alice", true, "0.8.0", true, false,
                TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER, ToolTier.STONE,
                BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE),
                TeCurrencyWitnessTracker.InventorySnapshot.of(backpack), false, false);

        TeCurrencyWitnessTracker.ArmResult result =
                TeCurrencyWitnessTracker.armForTest(unhealthyQuote, backpack, 1);

        assertFalse(result.armed());
        assertTrue(result.lines().contains("Currency capability: ACTIVE"));
        assertTrue(result.lines().contains("Quote bridge: UNHEALTHY"));
        assertTrue(result.lines().stream().anyMatch(line -> line.contains("quote bridge")));
    }

    @Test
    void mustHappen_quoteFailureAfterArmDoesNotRewriteCurrencyCapabilityEvidence() {
        UUID mobId = UUID.randomUUID();
        SimpleContainer backpack = backpack(witnessSticks(4));
        arm(mobId, backpack);

        TeCurrencyWitnessTracker.observeCompatibilityHealth(
                mobId, backpack, "0.8.0", true, false, 20);

        assertEquals(TeCurrencyWitnessTracker.State.FAIL,
                TeCurrencyWitnessTracker.stateForTests());
        List<String> report = TeCurrencyWitnessTracker.reportLines();
        assertTrue(report.contains("Currency capability: PASS"));
        assertTrue(report.contains("Bridge failure: YES"));
    }

    private static void arm(UUID mobId, SimpleContainer backpack) {
        TeCurrencyWitnessTracker.ArmResult result =
                TeCurrencyWitnessTracker.armForTest(validPreflight(mobId, backpack), backpack, 100);
        assertTrue(result.armed(), () -> String.join("\n", result.lines()));
    }

    private static TeCurrencyWitnessTracker.Preflight validPreflight(
            UUID mobId, SimpleContainer backpack) {
        return new TeCurrencyWitnessTracker.Preflight(
                mobId,
                "Alice",
                true,
                "0.8.0",
                true,
                true,
                TeCurrencyWitnessTracker.IRON_PICKAXE_CONSUMER,
                ToolTier.STONE,
                BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE),
                TeCurrencyWitnessTracker.InventorySnapshot.of(backpack),
                false,
                false);
    }

    private static SimpleContainer backpack(ItemStack witness) {
        SimpleContainer backpack = new SimpleContainer(8);
        backpack.setItem(0, witness);
        return backpack;
    }

    private static ItemStack witnessSticks(int count) {
        ItemStack stack = new ItemStack(Items.STICK, count);
        stack.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 8);
        return stack;
    }

    private static int countWitnessSticks(SimpleContainer backpack) {
        return backpack.getItem(0).is(Items.STICK) ? backpack.getItem(0).getCount() : 0;
    }

    private static MerchantOffer offer(ItemStack cost, ItemStack result) {
        return new MerchantOffer(
                new ItemCost(cost.getItemHolder(), cost.getCount(),
                        net.minecraft.core.component.DataComponentPredicate
                                .allOf(cost.getComponents())),
                Optional.empty(), result, 0, 12, 0, 0f);
    }

    private static MerchantOffer buy(int emeralds) {
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(Items.IRON_PICKAXE), 0, 12, 0, 0f);
    }

    private static OfferSnapshot snapshot(OfferRef ref, int ordinal, MerchantOffer offer) {
        return new OfferSnapshot(
                ref, ordinal, offer.getCostA(), offer.getCostB(), offer.assemble(),
                offer.getUses(), offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier(),
                offer.getDemand(), offer.getSpecialPriceDiff(), offer.shouldRewardExp());
    }

    /** Mirrors the sole transaction owner's order while exposing each passive witness hook. */
    private static void transact(SimpleContainer backpack, MerchantOffer live, long tick) {
        ItemStack[] staged = TradeTransaction.stage(backpack);
        TeCurrencyWitnessTracker.observePaymentStageEntered(backpack, live, staged, tick);
        assertTrue(TE.normalizeForPayment(staged, live.getCostA(), live.getCostB()));
        TeCurrencyWitnessTracker.observeBlockNormalization(backpack, live, staged, tick);
        assertTrue(TradeTransaction.debit(staged, live.getCostA()));
        assertTrue(TradeTransaction.debit(staged, live.getCostB()));
        assertTrue(TradeTransaction.insert(staged, live.assemble()));
        TradeTransaction.commit(backpack, staged);
        TeCurrencyWitnessTracker.observeCommit(backpack, live, tick);
        TeCurrencyWitnessTracker.observeNotifyAttempt(backpack, live, tick);
        MerchantOffer notified = live;
        assertSame(live, notified);
        TeCurrencyWitnessTracker.observeNotifyCompleted(backpack, live, tick);
    }
}
