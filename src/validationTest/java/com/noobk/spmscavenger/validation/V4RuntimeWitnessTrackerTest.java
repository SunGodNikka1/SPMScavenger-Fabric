package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4RuntimeWitnessTrackerTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000068");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000000069");
    private static final V4OfferFingerprint INITIAL = V4OfferFingerprint.simple(
            "minecraft:emerald", 8, "minecraft:iron_pickaxe", 1);
    private static final V4OfferFingerprint CHANGED = V4OfferFingerprint.simple(
            "minecraft:emerald", 10, "minecraft:iron_pickaxe", 1);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        V4RuntimeWitnessTracker.reset();
    }

    @Test
    void changedOfferNotInitialOfferMustReachExecutionForPhaseAPass() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, INITIAL, 20L);
        V4RuntimeWitnessTracker.markChangedOffer(CHANGED, 30L);
        V4RuntimeWitnessTracker.openPhaseA(40L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, CHANGED, 80L);
        V4RuntimeWitnessTracker.observeTrade(backpack, TRADER, CHANGED, true, 90L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.initialBoardObserved());
        assertTrue(snapshot.changedBoardRediscovered());
        assertTrue(snapshot.changedOfferExecuted());
        assertFalse(snapshot.cachedInitialOfferExecuted());
    }

    @Test
    void initialFingerprintExecutionIsARecordedInvariantFailure() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.markChangedOffer(CHANGED, 20L);
        V4RuntimeWitnessTracker.openPhaseA(30L);
        V4RuntimeWitnessTracker.observeTrade(backpack, TRADER, INITIAL, true, 40L);

        assertTrue(V4RuntimeWitnessTracker.snapshot().cachedInitialOfferExecuted());
        assertFalse(V4RuntimeWitnessTracker.snapshot().changedOfferExecuted());
    }

    @Test
    void initialTradeBeforePhaseAIsWarmupNotCachedAuthorityFailure() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, INITIAL, 11L);
        V4RuntimeWitnessTracker.observeTrade(backpack, TRADER, INITIAL, true, 12L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.initialBoardObserved());
        assertTrue(snapshot.initialWarmupOfferExecuted());
        assertFalse(snapshot.cachedInitialOfferExecuted());
        assertFalse(snapshot.changedOfferExecuted());
    }

    @Test
    void mutableLiveOfferIsAttributedByItsPreMutationFingerprint() {
        Object backpack = new Object();
        MerchantOffer live = offer(8, new ItemStack(Items.IRON_PICKAXE));
        V4OfferFingerprint preMutation = V4OfferFingerprint.of(live);
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, preMutation, 10L);

        live.increaseUses();
        assertEquals(1, live.getUses());
        assertNotEquals(preMutation, V4OfferFingerprint.of(live));
        V4RuntimeWitnessTracker.observeTrade(
                backpack, TRADER, preMutation, true, 12L);

        assertTrue(V4RuntimeWitnessTracker.snapshot().initialWarmupOfferExecuted());
        assertTrue(V4RuntimeWitnessTracker.events().stream()
                .anyMatch(event -> event.contains("INITIAL_WARMUP_TRADE")));
        assertFalse(V4RuntimeWitnessTracker.events().stream()
                .anyMatch(event -> event.contains("PRE_PHASE_A_OTHER_TRADE")));
    }

    @Test
    void phaseATradeUsesExactChangedPreMutationFingerprint() {
        Object backpack = new Object();
        MerchantOffer live = offer(10, new ItemStack(Items.IRON_PICKAXE));
        V4OfferFingerprint changedPreMutation = V4OfferFingerprint.of(live);
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.markChangedOffer(changedPreMutation, 20L);
        V4RuntimeWitnessTracker.openPhaseA(30L);

        live.increaseUses();
        V4RuntimeWitnessTracker.observeTrade(
                backpack, TRADER, changedPreMutation, true, 40L);

        assertTrue(V4RuntimeWitnessTracker.snapshot().changedOfferExecuted());
        assertFalse(V4RuntimeWitnessTracker.snapshot().cachedInitialOfferExecuted());
        assertEquals(changedPreMutation,
                V4RuntimeWitnessTracker.snapshot().executedOffer());
    }

    @Test
    void differentPriceResultOrComponentsRemainExactMismatches() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);

        ItemStack namedPickaxe = new ItemStack(Items.IRON_PICKAXE);
        namedPickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        for (V4OfferFingerprint mismatch : java.util.List.of(
                V4OfferFingerprint.of(offer(9, new ItemStack(Items.IRON_PICKAXE))),
                V4OfferFingerprint.of(offer(8, new ItemStack(Items.IRON_AXE))),
                V4OfferFingerprint.of(offer(8, namedPickaxe)))) {
            V4RuntimeWitnessTracker.observeTrade(
                    backpack, TRADER, mismatch, true, 20L);
        }

        assertFalse(V4RuntimeWitnessTracker.snapshot().initialWarmupOfferExecuted());
        assertEquals(3L, V4RuntimeWitnessTracker.events().stream()
                .filter(event -> event.contains("PRE_PHASE_A_OTHER_TRADE")).count());
    }

    @Test
    void nonTradedResultCreatesNoTransactionEvidence() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.observeTrade(
                backpack, TRADER, INITIAL, false, 20L);

        assertFalse(V4RuntimeWitnessTracker.snapshot().initialWarmupOfferExecuted());
        assertEquals(1, V4RuntimeWitnessTracker.snapshot().eventCount());
    }

    @Test
    void boardReadIsEvidenceEvenWhenPersistenceReportsNoChange() {
        V4RuntimeWitnessTracker.arm(MOB, TRADER, new Object(), INITIAL, 10L);
        V4RuntimeWitnessTracker.observeBoardInvocation(MOB, TRADER, false, 11L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, INITIAL, 11L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.initialBoardObserved());
        assertEquals(INITIAL, snapshot.initialBoardFingerprint());
        assertTrue(snapshot.knownTraderObservationAttempted());
        assertFalse(snapshot.knownTraderObservationChanged());
    }

    @Test
    void persistenceMutationIsReportedSeparatelyFromBoardRead() {
        V4RuntimeWitnessTracker.arm(MOB, TRADER, new Object(), INITIAL, 10L);
        V4RuntimeWitnessTracker.observeBoardInvocation(MOB, TRADER, true, 11L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, INITIAL, 11L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.initialBoardObserved());
        assertTrue(snapshot.knownTraderObservationAttempted());
        assertTrue(snapshot.knownTraderObservationChanged());
    }

    @Test
    void firstTickBoardEvidenceSurvivesStartupStabilityWithoutRearming() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.observeBoard(MOB, TRADER, INITIAL, 11L);

        // Startup stability is a controller lifecycle transition, not a new witness session.
        V4RuntimeWitnessTracker.Snapshot afterStability = V4RuntimeWitnessTracker.snapshot();
        assertTrue(afterStability.armed());
        assertTrue(afterStability.initialBoardObserved());
        assertEquals(2, afterStability.eventCount());
    }

    @Test
    void prePhaseAIntentCommuteAndArrivalAreBootstrapLocalEvidenceOnly() {
        VillageIntent intent = requiredTradeIntent();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, new Object(), INITIAL, 10L);
        V4RuntimeWitnessTracker.observeDirective(MOB, intent, 20L);
        V4RuntimeWitnessTracker.observeCommuteSeed(MOB, intent, true, 21L);
        V4RuntimeWitnessTracker.observeArrival(MOB, intent, true, 22L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertEquals(1, snapshot.bootstrapLocalRequiredTradeCount());
        assertEquals(1, snapshot.bootstrapLocalCommuteSeedCount());
        assertEquals(1, snapshot.bootstrapLocalArrivalCount());
        assertTrue(snapshot.bootstrapLocalIntentReleased());
        assertEquals(null, snapshot.intentIdentity());
        assertFalse(snapshot.commuteSeeded());
        assertFalse(snapshot.arrivalObserved());
    }

    @Test
    void phaseABindingCannotInheritBootstrapLocalIntent() {
        VillageIntent bootstrap = requiredTradeIntent();
        VillageIntent phaseA = new VillageIntent(
                VillageIntent.Kind.REQUIRED_TRADE,
                bootstrap.destination(),
                bootstrap.openedAtTick() + 100L,
                bootstrap.requiredTradeDemand());
        V4RuntimeWitnessTracker.arm(MOB, TRADER, new Object(), INITIAL, 10L);
        V4RuntimeWitnessTracker.observeDirective(MOB, bootstrap, 20L);
        V4RuntimeWitnessTracker.observeCommuteSeed(MOB, bootstrap, true, 21L);
        V4RuntimeWitnessTracker.observeArrival(MOB, bootstrap, true, 22L);
        V4RuntimeWitnessTracker.openPhaseA(100L);
        V4RuntimeWitnessTracker.observeDirective(MOB, phaseA, 101L);
        V4RuntimeWitnessTracker.observeCommuteSeed(MOB, phaseA, true, 102L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.intentIdentity().contains("@200/"));
        assertFalse(snapshot.intentIdentity().contains("@100/"));
        assertTrue(snapshot.commuteSeeded());
        assertEquals(1, snapshot.bootstrapLocalRequiredTradeCount());
    }

    @Test
    void exactIntentBindingSurvivesInterruptionAndFailureEvidenceDoesNotAppear() {
        WorkDemandPolicy.MaterialDemandIdentity demand = new WorkDemandPolicy.MaterialDemandIdentity(
                ResourceLocation.parse("minecraft:iron_ingot"),
                ResourceLocation.parse("spmscavenger:iron_pickaxe_upgrade"));
        VillageIntent intent = new VillageIntent(
                VillageIntent.Kind.REQUIRED_TRADE,
                new SettlementKey(Level.OVERWORLD, BlockPos.ZERO),
                100L,
                Optional.of(demand));
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        V4RuntimeWitnessTracker.observeDemand(
                demand, ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE, 90L);
        V4RuntimeWitnessTracker.openPhaseA(99L);
        V4RuntimeWitnessTracker.observeDirective(MOB, intent, 100L);
        V4RuntimeWitnessTracker.observeCommuteSeed(MOB, intent, true, 101L);
        V4RuntimeWitnessTracker.observeInterruption(MOB, intent, 120L);
        V4RuntimeWitnessTracker.observeNavigationStop(MOB, 121L);
        V4RuntimeWitnessTracker.observeResume(MOB, intent, 140L);

        V4RuntimeWitnessTracker.Snapshot snapshot = V4RuntimeWitnessTracker.snapshot();
        assertTrue(snapshot.sameBindingResumed());
        assertTrue(snapshot.navigationDiscarded());
        assertEquals(0, snapshot.routeFailurePublications());
        assertEquals(ExistingRouteFeasibility.ExistingRouteStatus.INFEASIBLE,
                snapshot.routeStatus());
    }

    @Test
    void activeRevalidationBeforeAnInterruptionDoesNotFabricateResumeEvidence() {
        VillageIntent intent = new VillageIntent(
                VillageIntent.Kind.REQUIRED_TRADE,
                new SettlementKey(Level.OVERWORLD, BlockPos.ZERO),
                100L,
                Optional.of(new WorkDemandPolicy.MaterialDemandIdentity(
                        ResourceLocation.parse("minecraft:iron_ingot"),
                        ResourceLocation.parse("spmscavenger:iron_pickaxe_upgrade"))));
        V4RuntimeWitnessTracker.arm(MOB, TRADER, new Object(), INITIAL, 10L);
        V4RuntimeWitnessTracker.openPhaseA(19L);
        V4RuntimeWitnessTracker.observeDirective(MOB, intent, 20L);
        V4RuntimeWitnessTracker.observeResume(MOB, intent, 21L);

        assertFalse(V4RuntimeWitnessTracker.snapshot().resumed());
        assertFalse(V4RuntimeWitnessTracker.snapshot().sameBindingResumed());
    }

    @Test
    void terminalResetReleasesEveryLiveReference() {
        Object backpack = new Object();
        V4RuntimeWitnessTracker.arm(MOB, TRADER, backpack, INITIAL, 10L);
        assertTrue(V4RuntimeWitnessTracker.snapshot().armed());
        V4RuntimeWitnessTracker.reset();
        assertFalse(V4RuntimeWitnessTracker.snapshot().armed());
        assertEquals(0, V4RuntimeWitnessTracker.snapshot().eventCount());
    }

    private static VillageIntent requiredTradeIntent() {
        return new VillageIntent(
                VillageIntent.Kind.REQUIRED_TRADE,
                new SettlementKey(Level.OVERWORLD, BlockPos.ZERO),
                100L,
                Optional.of(new WorkDemandPolicy.MaterialDemandIdentity(
                        ResourceLocation.parse("minecraft:iron_ingot"),
                        ResourceLocation.parse("spmscavenger:iron_pickaxe_upgrade"))));
    }

    private static MerchantOffer offer(int emeraldCost, ItemStack result) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeraldCost), Optional.empty(),
                result, 12, 0, 0.05F);
    }
}
