package com.noobk.spmscavenger.village.intent;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.routing.CapabilityEvidenceClass;
import com.noobk.spmscavenger.village.routing.FactualVillageUtility;
import com.noobk.spmscavenger.village.routing.SettlementDestinationFacts;
import com.noobk.spmscavenger.village.routing.SettlementDestinationRanker;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility.ExistingRouteStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageIntentRegistryTest {

    private static final UUID MOB = UUID.fromString("10000000-0000-0000-0000-000000000065");
    private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("iron_ingot");
    private static final ResourceLocation CONSUMER =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");
    private static final SettlementKey A = new SettlementKey(Level.OVERWORLD, new BlockPos(64, 64, 0));
    private static final SettlementKey B = new SettlementKey(Level.OVERWORLD, new BlockPos(-64, 64, 0));

    @AfterEach
    void clear() {
        VillageIntentRegistry.shutdownServerState();
    }

    @Test
    void oneIntentPerMobAndNewRankingCannotRetargetIt() {
        VillageIntent first = VillageIntentRegistry.openRequiredTrade(
                MOB, live(Set.of(A), false), selection(A), 10L).orElseThrow();
        VillageIntent secondCall = VillageIntentRegistry.openRequiredTrade(
                MOB, live(Set.of(A, B), false), selection(B), 20L).orElseThrow();

        assertEquals(first, secondCall);
        assertEquals(A, VillageIntentRegistry.current(MOB).orElseThrow().destination());
        assertEquals(1, VillageIntentRegistry.trackedIntentCount());
    }

    @Test
    void interruptionRetainsEntryButFreshInvalidationRemovesIt() {
        VillageIntentRegistry.openRequiredTrade(MOB, live(Set.of(A), false), selection(A), 10L);
        VillageIntentEvaluation interrupted = VillageIntentRegistry.revalidate(
                MOB, live(Set.of(A), true));
        assertTrue(interrupted.intentStillExists());
        assertTrue(VillageIntentRegistry.current(MOB).isPresent());

        VillageIntentEvaluation gone = VillageIntentRegistry.revalidate(
                MOB, VillageIntentFacts.noDemand(
                        ExistingRouteStatus.INFEASIBLE, Set.of(A), false));
        assertEquals(VillageIntentEvaluation.Cause.DEMAND_GONE, gone.cause());
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());
    }

    @Test
    void invalidDestinationClosesThenFutureOpenMayReselect() {
        VillageIntentRegistry.openRequiredTrade(MOB, live(Set.of(A), false), selection(A), 10L);
        VillageIntentRegistry.revalidate(MOB, live(Set.of(B), false));
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());

        VillageIntent reopened = VillageIntentRegistry.openRequiredTrade(
                MOB, live(Set.of(B), false), selection(B), 30L).orElseThrow();
        assertEquals(B, reopened.destination());
        assertEquals(30L, reopened.openedAtTick());
    }

    @Test
    void openBoundaryRevalidatesExistingAndDoesNotRetargetInSameCall() {
        VillageIntentRegistry.openRequiredTrade(MOB, live(Set.of(A), false), selection(A), 10L);

        Optional<VillageIntent> invalidatingCall = VillageIntentRegistry.openRequiredTrade(
                MOB,
                new VillageIntentFacts(
                        Optional.of(new WorkDemandPolicy.MaterialDemand(
                                IRON, 3, ResourceLocation.fromNamespaceAndPath(
                                        "spmscavenger", "iron_axe_upgrade"))),
                        ExistingRouteStatus.INFEASIBLE, Set.of(B), false),
                selection(B), 20L);

        assertTrue(invalidatingCall.isEmpty());
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());
    }

    @Test
    void explicitOwnerAndServerLifecycleClearTransientState() {
        VillageIntentRegistry.openRequiredTrade(MOB, live(Set.of(A), false), selection(A), 10L);
        VillageIntentRegistry.release(MOB);
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());

        VillageIntentRegistry.openRequiredTrade(MOB, live(Set.of(A), false), selection(A), 20L);
        VillageIntentRegistry.shutdownServerState();
        assertEquals(0, VillageIntentRegistry.trackedIntentCount());
    }

    @Test
    void conditionalReleaseRequiresExactIntentInstance() {
        VillageIntent current = VillageIntentRegistry.openRequiredTrade(
                MOB, live(Set.of(A), false), selection(A), 10L).orElseThrow();
        VillageIntent equalButDifferent = new VillageIntent(
                current.kind(), current.destination(), current.openedAtTick(),
                current.requiredTradeDemand());

        assertFalse(VillageIntentRegistry.releaseIfCurrent(MOB, equalButDifferent));
        assertTrue(VillageIntentRegistry.current(MOB).isPresent());
        assertTrue(VillageIntentRegistry.releaseIfCurrent(MOB, current));
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());
    }

    private static VillageIntentFacts live(Set<SettlementKey> compatible, boolean interrupted) {
        return new VillageIntentFacts(
                Optional.of(new WorkDemandPolicy.MaterialDemand(IRON, 3, CONSUMER)),
                ExistingRouteStatus.INFEASIBLE, compatible, interrupted);
    }

    private static SettlementDestinationRanker.Selection selection(SettlementKey key) {
        KnownVillage village = KnownVillage.discovered(
                key.anchor(), 1L, ObservationQuality.fullCoverage(4));
        SettlementDestinationFacts facts = new SettlementDestinationFacts(
                key, village, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        return new SettlementDestinationRanker.Selection(
                facts, FactualVillageUtility.from(BlockPos.ZERO, facts), 0);
    }
}
