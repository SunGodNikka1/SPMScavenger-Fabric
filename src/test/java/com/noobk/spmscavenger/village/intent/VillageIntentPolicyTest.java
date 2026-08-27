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
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageIntentPolicyTest {

    private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("iron_ingot");
    private static final ResourceLocation GOLD = ResourceLocation.withDefaultNamespace("gold_ingot");
    private static final ResourceLocation PICKAXE =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_pickaxe_upgrade");
    private static final ResourceLocation AXE =
            ResourceLocation.fromNamespaceAndPath("spmscavenger", "iron_axe_upgrade");
    private static final SettlementKey A = new SettlementKey(Level.OVERWORLD, new BlockPos(64, 64, 0));
    private static final SettlementKey B = new SettlementKey(Level.OVERWORLD, new BlockPos(-64, 64, 0));

    @Test
    void materialDemandIdentityExcludesChangingDeficit() {
        assertEquals(demand(IRON, 3, PICKAXE).identity(), demand(IRON, 2, PICKAXE).identity());
        assertFalse(demand(IRON, 3, PICKAXE).identity().equals(demand(IRON, 3, AXE).identity()));
        assertFalse(demand(IRON, 3, PICKAXE).identity().equals(demand(GOLD, 3, PICKAXE).identity()));
    }

    @Test
    void validBlockingDemandAndCompatibleSelectionOpenRequiredTrade() {
        VillageIntent intent = VillageIntentPolicy.openRequiredTrade(
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false),
                selection(A, CapabilityEvidenceClass.POSITIVE_HINT), 100L).orElseThrow();

        assertEquals(VillageIntent.Kind.REQUIRED_TRADE, intent.kind());
        assertEquals(A, intent.destination());
        assertEquals(100L, intent.openedAtTick());
        assertEquals(demand(IRON, 1, PICKAXE).identity(), intent.requiredTradeDemand().orElseThrow());
    }

    @Test
    void rankingAloneOrNonDisplacedRouteCannotOpenIntent() {
        SettlementDestinationRanker.Selection ranked = selection(A, CapabilityEvidenceClass.POSITIVE_HINT);
        assertTrue(VillageIntentPolicy.openRequiredTrade(
                VillageIntentFacts.noDemand(ExistingRouteStatus.INFEASIBLE, Set.of(A), false),
                ranked, 1L).isEmpty());
        assertTrue(VillageIntentPolicy.openRequiredTrade(
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.FEASIBLE, Set.of(A), false),
                ranked, 1L).isEmpty());
        assertTrue(VillageIntentPolicy.openRequiredTrade(
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.UNKNOWN, Set.of(A), false),
                ranked, 1L).isEmpty());
        assertTrue(VillageIntentPolicy.openRequiredTrade(
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(B), false),
                ranked, 1L).isEmpty());
    }

    @Test
    void deficitChangeRetainsIntentButMaterialOrConsumerChangeClosesIt() {
        VillageIntent intent = openedA();

        assertActive(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 2, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false)));
        assertClosed(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 3, AXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false)),
                VillageIntentEvaluation.Cause.DEMAND_CHANGED);
        assertClosed(VillageIntentPolicy.revalidate(intent,
                facts(demand(GOLD, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false)),
                VillageIntentEvaluation.Cause.DEMAND_CHANGED);
    }

    @Test
    void disappearedDemandOrLostRouteJustificationClosesImmediately() {
        VillageIntent intent = openedA();

        assertClosed(VillageIntentPolicy.revalidate(intent,
                VillageIntentFacts.noDemand(ExistingRouteStatus.INFEASIBLE, Set.of(A), false)),
                VillageIntentEvaluation.Cause.DEMAND_GONE);
        assertClosed(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.UNKNOWN, Set.of(A), false)),
                VillageIntentEvaluation.Cause.ROUTE_JUSTIFICATION_LOST);
        assertClosed(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.FEASIBLE, Set.of(A), false)),
                VillageIntentEvaluation.Cause.ROUTE_JUSTIFICATION_LOST);
    }

    @Test
    void removedOrIncompatibleDestinationCloses() {
        assertClosed(VillageIntentPolicy.revalidate(openedA(),
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(B), false)),
                VillageIntentEvaluation.Cause.DESTINATION_INVALID);
    }

    @Test
    void combatSuspendsWithoutDestroyingAndResumeRevalidatesFreshFacts() {
        VillageIntent intent = openedA();
        VillageIntentEvaluation suspended = VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), true));
        assertTrue(suspended.intentStillExists());
        assertFalse(suspended.currentlyAdmissible());
        assertEquals(VillageIntentEvaluation.Cause.INTERRUPTED, suspended.cause());

        assertActive(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 1, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false)));
        assertClosed(VillageIntentPolicy.revalidate(intent,
                VillageIntentFacts.noDemand(ExistingRouteStatus.INFEASIBLE, Set.of(A), false)),
                VillageIntentEvaluation.Cause.DEMAND_GONE);
    }

    @Test
    void capabilityExpiryOpinionAndNewRankingAreNotRevalidationInputs() {
        VillageIntent intent = openedA();
        // The selected hint may now be UNKNOWN and B may rank better. Legitimacy of committed A
        // depends only on live demand/route/hard candidacy, never on reranking.
        SettlementDestinationRanker.Selection nowUnknown = selection(A, CapabilityEvidenceClass.UNKNOWN);
        SettlementDestinationRanker.Selection nowBetter = selection(B, CapabilityEvidenceClass.POSITIVE_HINT);
        assertEquals(A, intent.destination());
        assertEquals(A, nowUnknown.facts().key());
        assertEquals(B, nowBetter.facts().key());
        assertActive(VillageIntentPolicy.revalidate(intent,
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A, B), false)));
    }

    private static VillageIntent openedA() {
        return VillageIntentPolicy.openRequiredTrade(
                facts(demand(IRON, 3, PICKAXE), ExistingRouteStatus.INFEASIBLE, Set.of(A), false),
                selection(A, CapabilityEvidenceClass.POSITIVE_HINT), 10L).orElseThrow();
    }

    private static VillageIntentFacts facts(WorkDemandPolicy.MaterialDemand demand,
            ExistingRouteStatus status, Set<SettlementKey> compatible, boolean interrupted) {
        return new VillageIntentFacts(Optional.of(demand), status, compatible, interrupted);
    }

    private static WorkDemandPolicy.MaterialDemand demand(
            ResourceLocation material, int deficit, ResourceLocation consumer) {
        return new WorkDemandPolicy.MaterialDemand(material, deficit, consumer);
    }

    private static SettlementDestinationRanker.Selection selection(
            SettlementKey key, CapabilityEvidenceClass evidence) {
        KnownVillage village = KnownVillage.discovered(
                key.anchor(), 1L, ObservationQuality.fullCoverage(4));
        SettlementDestinationFacts facts = new SettlementDestinationFacts(
                key, village, evidence, false, 0);
        return new SettlementDestinationRanker.Selection(
                facts, FactualVillageUtility.from(BlockPos.ZERO, facts), 0);
    }

    private static void assertActive(VillageIntentEvaluation evaluation) {
        assertTrue(evaluation.intentStillExists());
        assertTrue(evaluation.currentlyAdmissible());
        assertEquals(VillageIntentEvaluation.Cause.ACTIVE, evaluation.cause());
    }

    private static void assertClosed(
            VillageIntentEvaluation evaluation, VillageIntentEvaluation.Cause expected) {
        assertFalse(evaluation.intentStillExists());
        assertFalse(evaluation.currentlyAdmissible());
        assertEquals(expected, evaluation.cause());
    }
}
