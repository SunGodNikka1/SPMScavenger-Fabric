package com.noobk.spmscavenger.village.routing;

import com.noobk.spmscavenger.opinion.AffectiveState;
import com.noobk.spmscavenger.opinion.DiscretionaryAvailability;
import com.noobk.spmscavenger.opinion.DiscretionaryScoringInput;
import com.noobk.spmscavenger.opinion.OpinionFeatureGate;
import com.noobk.spmscavenger.opinion.OpinionMemory;
import com.noobk.spmscavenger.opinion.SettlementOpinionContext;
import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.ObservationQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementDestinationRankerTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @AfterEach
    void resetOpinionGate() {
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void emptyAndSingleCandidateSelection() {
        OpinionFeatureGate.setTestOverride(true);
        assertTrue(select(List.of(), SettlementOpinionContext.neutral(), RouteAttemptEvidence.none(), 0L)
                .isEmpty());

        SettlementDestinationFacts only = facts(32, 0, CapabilityEvidenceClass.UNKNOWN, false, 0);
        assertEquals(only.key(), select(List.of(only), SettlementOpinionContext.neutral(),
                RouteAttemptEvidence.none(), 0L).orElseThrow().facts().key());
    }

    @Test
    void positiveEvidenceOutranksUnknownDespiteOpinionHomeAndDistance() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts positive = facts(
                160, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementDestinationFacts unknownHome = facts(
                1, 0, CapabilityEvidenceClass.UNKNOWN, true, 1000);
        SettlementOpinionContext opinions = new SettlementOpinionContext(Map.of(
                chunkKey(positive.village().anchor()), -100f,
                chunkKey(unknownHome.village().anchor()), 100f));

        SettlementDestinationRanker.Selection selected = select(
                List.of(unknownHome, positive), opinions, RouteAttemptEvidence.none(), 0L)
                .orElseThrow();

        assertEquals(positive.key(), selected.facts().key());
        assertEquals(-15, selected.opinionBias());
    }

    @Test
    void factualTuplePrecedesOpinionWithinPositiveClass() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts near = facts(
                32, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementDestinationFacts far = facts(
                96, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementOpinionContext opinions = new SettlementOpinionContext(Map.of(
                chunkKey(near.village().anchor()), -100f,
                chunkKey(far.village().anchor()), 100f));

        assertEquals(near.key(), select(List.of(far, near), opinions,
                RouteAttemptEvidence.none(), 0L).orElseThrow().facts().key());
    }

    @Test
    void comparablePositiveCandidatesMayBeReorderedByBoundedOpinion() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts liked = facts(
                16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementDestinationFacts disliked = facts(
                -16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementOpinionContext opinions = new SettlementOpinionContext(Map.of(
                chunkKey(liked.village().anchor()), 100f,
                chunkKey(disliked.village().anchor()), -100f));

        SettlementDestinationRanker.Selection selected = select(
                List.of(disliked, liked), opinions, RouteAttemptEvidence.none(), 0L).orElseThrow();
        assertEquals(liked.key(), selected.facts().key());
        assertEquals(15, selected.opinionBias());
    }

    @Test
    void exactTieUsesCanonicalAnchorIndependentOfIterationOrder() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts negativeX = facts(
                -16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementDestinationFacts positiveX = facts(
                16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);

        SettlementKey expected = negativeX.key();
        assertEquals(expected, select(List.of(positiveX, negativeX),
                SettlementOpinionContext.neutral(), RouteAttemptEvidence.none(), 0L)
                .orElseThrow().facts().key());
        assertEquals(expected, select(List.of(negativeX, positiveX),
                SettlementOpinionContext.neutral(), RouteAttemptEvidence.none(), 0L)
                .orElseThrow().facts().key());
    }

    @Test
    void dimensionMismatchIsObjectiveIncompatibility() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts nether = facts(
                Level.NETHER, 1, 0, CapabilityEvidenceClass.POSITIVE_HINT, true, 1000);
        assertTrue(select(List.of(nether), SettlementOpinionContext.neutral(),
                RouteAttemptEvidence.none(), 0L).isEmpty());

        SettlementDestinationFacts overworld = facts(
                100, 0, CapabilityEvidenceClass.UNKNOWN, false, 0);
        assertEquals(overworld.key(), select(List.of(nether, overworld),
                SettlementOpinionContext.neutral(), RouteAttemptEvidence.none(), 0L)
                .orElseThrow().facts().key());
    }

    @Test
    void transientDemotionSelectsAlternativeAndExpiryRestoresCandidate() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts best = facts(
                16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        SettlementDestinationFacts fallback = facts(
                64, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 0);
        RouteAttemptEvidence evidence = RouteAttemptEvidence.of(List.of(
                new RouteAttemptEvidence.Attempt(best.key(), 200L, 1)));

        assertEquals(fallback.key(), select(List.of(best, fallback),
                SettlementOpinionContext.neutral(), evidence, 199L)
                .orElseThrow().facts().key());
        assertEquals(best.key(), select(List.of(best, fallback),
                SettlementOpinionContext.neutral(), evidence, 200L)
                .orElseThrow().facts().key());
    }

    @Test
    void factualHomeAndFamiliarityAreOnlySameDistanceTieBreaks() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementDestinationFacts home = facts(
                -16, 0, CapabilityEvidenceClass.POSITIVE_HINT, true, 200);
        SettlementDestinationFacts familiar = facts(
                16, 0, CapabilityEvidenceClass.POSITIVE_HINT, false, 1000);

        assertEquals(home.key(), select(List.of(familiar, home),
                SettlementOpinionContext.neutral(), RouteAttemptEvidence.none(), 0L)
                .orElseThrow().facts().key());
    }

    @Test
    void horizontalEstimateIgnoresOriginAndDestinationY() {
        KnownVillage low = KnownVillage.discovered(
                new BlockPos(30, -64, 40), 1L, ObservationQuality.fullCoverage(4));
        KnownVillage high = KnownVillage.discovered(
                new BlockPos(30, 320, 40), 1L, ObservationQuality.fullCoverage(4));
        SettlementDestinationFacts lowFacts = new SettlementDestinationFacts(
                new SettlementKey(Level.OVERWORLD, low.anchor()), low,
                CapabilityEvidenceClass.UNKNOWN, false, 0);
        SettlementDestinationFacts highFacts = new SettlementDestinationFacts(
                new SettlementKey(Level.OVERWORLD, high.anchor()), high,
                CapabilityEvidenceClass.UNKNOWN, false, 0);

        assertEquals(
                FactualVillageUtility.from(new BlockPos(0, -64, 0), lowFacts)
                        .horizontalDistanceSquared(),
                FactualVillageUtility.from(new BlockPos(0, 320, 0), highFacts)
                        .horizontalDistanceSquared());
        assertEquals(2_500L, FactualVillageUtility.from(ORIGIN, lowFacts)
                .horizontalDistanceSquared());
    }

    private static java.util.Optional<SettlementDestinationRanker.Selection> select(
            List<SettlementDestinationFacts> candidates,
            SettlementOpinionContext opinions,
            RouteAttemptEvidence attempts,
            long now) {
        return SettlementDestinationRanker.select(
                candidates, Level.OVERWORLD, ORIGIN, enabledInput(), opinions, attempts, now);
    }

    private static SettlementDestinationFacts facts(
            int x, int z, CapabilityEvidenceClass evidence, boolean home, int familiarity) {
        return facts(Level.OVERWORLD, x, z, evidence, home, familiarity);
    }

    private static SettlementDestinationFacts facts(
            net.minecraft.resources.ResourceKey<Level> dimension,
            int x,
            int z,
            CapabilityEvidenceClass evidence,
            boolean home,
            int familiarity) {
        KnownVillage village = KnownVillage.discovered(
                new BlockPos(x, 64, z), 1L, ObservationQuality.fullCoverage(4));
        return new SettlementDestinationFacts(
                new SettlementKey(dimension, village.anchor()), village, evidence, home, familiarity);
    }

    private static DiscretionaryScoringInput enabledInput() {
        return new DiscretionaryScoringInput(
                new AffectiveState(), new OpinionMemory(), DiscretionaryAvailability.none(),
                true, true);
    }

    private static long chunkKey(BlockPos anchor) {
        return new ChunkPos(anchor).toLong();
    }
}
