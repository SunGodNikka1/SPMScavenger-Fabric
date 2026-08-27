package com.noobk.spmscavenger.village.interaction;

import com.noobk.spmscavenger.WorkDemandPolicy;
import com.noobk.spmscavenger.opinion.AffectiveState;
import com.noobk.spmscavenger.opinion.DiscretionaryAvailability;
import com.noobk.spmscavenger.opinion.DiscretionaryScoringInput;
import com.noobk.spmscavenger.opinion.OpinionMemory;
import com.noobk.spmscavenger.opinion.SettlementOpinionContext;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.ObservationQuality;
import com.noobk.spmscavenger.village.intent.VillageIntentFacts;
import com.noobk.spmscavenger.village.intent.VillageIntentRegistry;
import com.noobk.spmscavenger.village.routing.CapabilityEvidenceClass;
import com.noobk.spmscavenger.village.routing.RouteAttemptEvidence;
import com.noobk.spmscavenger.village.routing.SettlementDestinationFacts;
import com.noobk.spmscavenger.village.routing.SettlementKey;
import com.noobk.spmscavenger.village.trade.ExistingRouteFeasibility.ExistingRouteStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageInteractionDirectorTest {

    private static final UUID MOB = UUID.fromString("66000000-0000-0000-0000-000000000001");
    private static final SettlementKey LOW_NON_HOME =
            new SettlementKey(Level.OVERWORLD, new BlockPos(180, 64, 0));

    @AfterEach
    void clear() {
        VillageIntentRegistry.shutdownServerState();
    }

    @Test
    void liveJustificationCanSeedRequiredTradeToLowNonHomeSettlement() {
        Optional<CommuteDirective> directive = VillageInteractionDirector.openOrResumeResolved(
                MOB,
                Level.OVERWORLD,
                BlockPos.ZERO,
                resolved(
                        Optional.of(new WorkDemandPolicy.MaterialDemand(
                                ResourceLocation.withDefaultNamespace("iron_ingot"),
                                3,
                                ResourceLocation.fromNamespaceAndPath(
                                        "spmscavenger", "iron_pickaxe_upgrade"))),
                        ExistingRouteStatus.INFEASIBLE,
                        List.of(facts(LOW_NON_HOME, false, 0))),
                RouteAttemptEvidence.none(),
                100L);

        assertEquals(LOW_NON_HOME.anchor(), directive.orElseThrow().destination());
        assertEquals(CommuteDirective.Purpose.REQUIRED_TRADE, directive.get().purpose());
    }

    @Test
    void rankingFactsAloneCannotCreateMovementAdmission() {
        Optional<CommuteDirective> directive = VillageInteractionDirector.openOrResumeResolved(
                MOB,
                Level.OVERWORLD,
                BlockPos.ZERO,
                resolved(Optional.empty(), ExistingRouteStatus.INFEASIBLE,
                        List.of(facts(LOW_NON_HOME, false, 0))),
                RouteAttemptEvidence.none(),
                100L);

        assertTrue(directive.isEmpty());
        assertTrue(VillageIntentRegistry.current(MOB).isEmpty());
    }

    private static VillageInteractionDirector.ResolvedFacts resolved(
            Optional<WorkDemandPolicy.MaterialDemand> demand,
            ExistingRouteStatus status,
            List<SettlementDestinationFacts> candidates) {
        return new VillageInteractionDirector.ResolvedFacts(
                new VillageIntentFacts(demand, status, Set.of(LOW_NON_HOME), false),
                candidates,
                new DiscretionaryScoringInput(
                        new AffectiveState(), new OpinionMemory(),
                        DiscretionaryAvailability.none(), false, false),
                SettlementOpinionContext.neutral());
    }

    private static SettlementDestinationFacts facts(
            SettlementKey key, boolean home, int familiarity) {
        return new SettlementDestinationFacts(
                key,
                KnownVillage.discovered(key.anchor(), 1L, ObservationQuality.fullCoverage(3)),
                CapabilityEvidenceClass.POSITIVE_HINT,
                home,
                familiarity);
    }
}
