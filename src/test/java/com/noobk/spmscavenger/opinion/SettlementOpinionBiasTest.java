package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.village.KnownVillage;
import com.noobk.spmscavenger.village.ObservationQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SettlementOpinionBiasTest {

    @AfterEach
    void resetGate() {
        OpinionFeatureGate.clearTestOverride();
    }

    @Test
    void positiveNegativeAndNeutralPlacePreferenceUseExistingScale() {
        OpinionFeatureGate.setTestOverride(true);
        KnownVillage positive = village(8, 8);
        KnownVillage negative = village(24, 8);
        KnownVillage neutral = village(40, 8);
        SettlementOpinionContext context = new SettlementOpinionContext(Map.of(
                new ChunkPos(0, 0).toLong(), 100f,
                new ChunkPos(1, 0).toLong(), -100f));

        assertEquals(15, SettlementOpinionBias.request(positive, enabledInput(), context));
        assertEquals(-15, SettlementOpinionBias.request(negative, enabledInput(), context));
        assertEquals(0, SettlementOpinionBias.request(neutral, enabledInput(), context));
    }

    @Test
    void eitherOpinionGateDisabledIsNeutral() {
        KnownVillage village = village(8, 8);
        SettlementOpinionContext context = new SettlementOpinionContext(Map.of(
                new ChunkPos(0, 0).toLong(), 100f));

        OpinionFeatureGate.setTestOverride(false);
        assertEquals(0, SettlementOpinionBias.request(village, enabledInput(), context));

        OpinionFeatureGate.setTestOverride(true);
        assertEquals(0, SettlementOpinionBias.request(village, disabledInput(), context));
    }

    @Test
    void arbitraryResolvedPlaceValuesCannotEscapeHardCap() {
        OpinionFeatureGate.setTestOverride(true);
        KnownVillage village = village(8, 8);
        long key = new ChunkPos(0, 0).toLong();

        assertEquals(PlaceOpinionRouteRanker.MAX_ROUTE_BIAS,
                SettlementOpinionBias.request(village, enabledInput(),
                        new SettlementOpinionContext(Map.of(key, 100_000f))));
        assertEquals(-PlaceOpinionRouteRanker.MAX_ROUTE_BIAS,
                SettlementOpinionBias.request(village, enabledInput(),
                        new SettlementOpinionContext(Map.of(key, -100_000f))));
    }

    @Test
    void currentAnchorSelectsCurrentChunkWithoutFrozenSettlementKey() {
        OpinionFeatureGate.setTestOverride(true);
        SettlementOpinionContext context = new SettlementOpinionContext(Map.of(
                new ChunkPos(0, 0).toLong(), 100f,
                new ChunkPos(1, 0).toLong(), -100f));

        assertEquals(15, SettlementOpinionBias.request(village(15, 0), enabledInput(), context));
        assertEquals(-15, SettlementOpinionBias.request(village(16, 0), enabledInput(), context));
    }

    @Test
    void contextDefensivelyCopiesAndEvaluationDoesNotMutateOpinionOrVillage() {
        OpinionFeatureGate.setTestOverride(true);
        PlaceOpinionMemory places = new PlaceOpinionMemory();
        long first = new ChunkPos(0, 0).toLong();
        long second = new ChunkPos(1, 0).toLong();
        places.recordOutcome(first, 80f);
        places.recordOutcome(second, -40f);
        Map<Long, Float> orderBefore = places.captureSnapshot();
        SettlementOpinionContext context = SettlementOpinionContext.from(places);
        KnownVillage village = village(8, 8);
        long seenBefore = village.lastSeenTick();

        SettlementOpinionBias.request(village, enabledInput(), context);

        assertEquals(orderBefore, places.captureSnapshot());
        assertEquals(seenBefore, village.lastSeenTick());
        assertEquals(2, places.trackedPlaceCount());
        assertNotSame(orderBefore, context.placePreferences());
    }

    private static KnownVillage village(int x, int z) {
        return new KnownVillage(new BlockPos(x, 64, z), 10L, 20L,
                ObservationQuality.fullCoverage(3));
    }

    private static DiscretionaryScoringInput enabledInput() {
        return input(true);
    }

    private static DiscretionaryScoringInput disabledInput() {
        return input(false);
    }

    private static DiscretionaryScoringInput input(boolean enabled) {
        return new DiscretionaryScoringInput(
                new AffectiveState(), new OpinionMemory(), DiscretionaryAvailability.none(),
                true, enabled);
    }
}
