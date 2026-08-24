package com.noobk.spmscavenger.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class V3CampaignProgressTest {

    @Test
    void fixedWindowCompletesAtExactBoundary() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.STORAGE_PUBLIC_DENY, 100, 0);
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(899, probe(0, 0, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(900, probe(0, 0, false)).disposition());
    }

    @Test
    void replantWindowStartsStabilizationAtObservedTransition() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_MANAGED_SINGLE, 100, 16);
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(500, probe(0, 16, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(501, probe(1, 15, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(700, probe(1, 15, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(701, probe(1, 15, false)).disposition());
    }

    @Test
    void combatRowRequestsTriggerThenWaitsForReleasePlusSixHundredTicks() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_INTERRUPT_COMBAT, 100, 16);
        assertFalse(progress.observe(219, probe(0, 16, false)).fireDeclaredTrigger());
        assertTrue(progress.observe(220, probe(0, 16, false)).fireDeclaredTrigger());
        progress.markTriggerFired();
        progress.observe(240, probe(0, 16, true));
        progress.observe(300, probe(0, 16, false));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(899, probe(0, 16, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(900, probe(0, 16, false)).disposition());
    }

    @Test
    void requiredTransitionTimeoutIsIncompleteNotProductFailure() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_MULTI_CYCLE, 100, 4);
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(4099, probe(1, 3, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.INCOMPLETE,
                progress.observe(4100, probe(1, 3, false)).disposition());
    }

    @Test
    void multiCycleRejectsDistinctPositionsAndRequiresSameCellTemporalRepeat() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_MULTI_CYCLE,
                0,
                probe(0, 0b111, 4, false, Set.of()));
        progress.observe(100, probe(0b001, 0b110, 3, false, Set.of()));
        progress.observe(200, probe(0b010, 0b100, 2, false, Set.of()));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(600, probe(0, 0b100, 2, false, Set.of())).disposition(),
                "two different replanted positions are not two temporal cycles");

        progress.observe(700, probe(0, 0b101, 2, false, Set.of()));
        progress.observe(800, probe(0b001, 0b100, 1, false, Set.of()));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(1199, probe(0, 0b100, 1, false, Set.of())).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(1200, probe(0, 0b100, 1, false, Set.of())).disposition());
    }

    @Test
    void multiMobRequiresTwoCommitmentsThenOneCommitAndStaleRelease() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_MULTI_MOB,
                0,
                probe(0, 1, 16, false, Set.of()));

        progress.observe(10, probe(0, 1, 16, false, Set.of(first, second)));
        progress.observe(20, probe(1, 0, 15, false, Set.of()));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(219, probe(0, 0, 15, false, Set.of())).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(220, probe(0, 0, 15, false, Set.of())).disposition());
    }

    @Test
    void multiMobReplantWithoutObservedDualCommitmentDoesNotComplete() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.CROP_MULTI_MOB,
                0,
                probe(0, 1, 16, false, Set.of()));
        progress.observe(20, probe(1, 0, 15, false, Set.of()));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(220, probe(0, 0, 15, false, Set.of())).disposition());
    }

    @Test
    void compostWaitsForRealSeedDebitBeforeStabilization() {
        V3CampaignProgress progress = V3CampaignProgress.open(
                V3CampaignScenario.COMPOST_SEED_SURPLUS, 0, 32);
        progress.observe(200, probe(0, 32, false));
        progress.observe(201, probe(0, 31, false));
        assertEquals(V3CampaignProgress.Disposition.OBSERVING,
                progress.observe(600, probe(0, 31, false)).disposition());
        assertEquals(V3CampaignProgress.Disposition.OBSERVATION_COMPLETE,
                progress.observe(601, probe(0, 31, false)).disposition());
    }

    private static V3CampaignProgress.Probe probe(
            int replantedTargets, int subjectSeeds, boolean combatTarget) {
        return new V3CampaignProgress.Probe(replantedTargets, subjectSeeds, combatTarget);
    }

    private static V3CampaignProgress.Probe probe(
            int replantedTargets,
            int matureTargets,
            int subjectSeeds,
            boolean combatTarget,
            Set<UUID> committedActors) {
        return new V3CampaignProgress.Probe(
                replantedTargets, matureTargets, subjectSeeds, combatTarget, committedActors);
    }
}
