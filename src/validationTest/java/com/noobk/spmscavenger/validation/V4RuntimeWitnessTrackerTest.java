package com.noobk.spmscavenger.validation;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.intent.VillageIntent;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4RuntimeWitnessTrackerTest {

    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000068");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000000069");
    private static final V4OfferFingerprint INITIAL = V4OfferFingerprint.simple(
            "minecraft:emerald", 8, "minecraft:iron_pickaxe", 1);
    private static final V4OfferFingerprint CHANGED = V4OfferFingerprint.simple(
            "minecraft:emerald", 10, "minecraft:iron_pickaxe", 1);

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
}
