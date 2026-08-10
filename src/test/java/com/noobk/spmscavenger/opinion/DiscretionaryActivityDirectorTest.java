package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.MobExperienceContext;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscretionaryActivityDirectorTest {

    private static final UUID MOB = UUID.randomUUID();

    private DiscretionaryDirectorState director;

    @BeforeEach
    void setUp() {
        OpinionFeatureGate.testOverride = true;
        director = OpinionExperienceRegistry.contextFor(MOB).discretionaryDirector();
        director.clearForTest();
    }

    @AfterEach
    void tearDown() {
        OpinionExperienceRegistry.clearAll();
        OpinionFeatureGate.testOverride = null;
    }

  // GAO-4-M7
    @Test
    void lowScoresAbstain() {
        seedOpinions(-80f, 90f, -70f, 90f);
        neutralMood().seedChannels(0f, 5f, 0f, 5f, 0f);

        director.tick(tick(10L, idleObservation(), true));

        assertTrue(director.intent().isEmpty());
        assertTrue(hasTraceStage(OpinionDecisionTrace.Stage.ABSTAIN));
    }

  // GAO-4-M9
    @Test
    void opinionOffPreservesLegacyConsumerAuthority() {
        OpinionFeatureGate.testOverride = false;
        assertTrue(DiscretionaryAuthority.mayStartDiscretionaryExplore(MOB));
        assertTrue(DiscretionaryAuthority.mayStartDiscretionaryRest(MOB));
        assertFalse(DiscretionaryAuthority.mustYieldDiscretionaryRest(MOB));
    }

  // GAO-4-M12
    @Test
    void restIncumbentExploreWinRequestsVoluntaryYield() {
        seedOpinions(55f, 5f, 11f, 4f);
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 80f, 0f, 5f, 15f);

        director.seedIncumbent(DiscretionaryActivity.REST, 20f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 10L;

        director.tick(tick(afterCommitment, idleObservation(), true));

        assertTrue(director.restYieldRequested());
        UUID exploreIntentId = director.pendingIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();
        UUID restIntentId = director.runningIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();

        DiscretionaryAuthority.onRestYieldedForExplore(MOB, restIntentId, afterCommitment + 1L);

        assertEquals(exploreIntentId, director.pendingIntent().map(DiscretionaryIntent::intentId).orElseThrow());
        assertTrue(director.runningIntent().isEmpty());
        DiscretionaryAuthority.onExploreAdopted(MOB, afterCommitment + 2L);
        assertEquals(exploreIntentId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
        assertEquals(IntentLifecycle.RUNNING, director.runningIntent().get().lifecycle());
    }

  // GAO-4-M1 / explore wins
    @Test
    void wanderContextExploreWinsAndIssuesIntent() {
        seedOpinions(55f, 5f, 11f, 4f);
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 80f, 0f, 5f, 10f);

        director.tick(tick(10L, idleObservation(), true));

        assertEquals(DiscretionaryActivity.EXPLORE, director.intent().get().activity());
        assertEquals(IntentLifecycle.PENDING, director.intent().get().lifecycle());
        assertTrue(hasTraceStage(OpinionDecisionTrace.Stage.INTENT));
    }

  // GAO-4-M2
    @Test
    void restWinsIssuesRestIntent() {
        seedOpinions(20f, 40f, 30f, 5f);
        AffectiveState mood = neutralMood();
        mood.seedChannels(0f, 5f, 0f, 80f, 0f);

        director.tick(tick(10L, idleObservation(), true));

        assertEquals(DiscretionaryActivity.REST, director.intent().get().activity());
    }

  // GAO-4-M3
    @Test
    void exploreBeatsRestAfterCommitmentWithMargin() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.seedIncumbent(DiscretionaryActivity.REST, 15f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 5L;

        director.tick(tick(afterCommitment, idleObservation(), true));

        assertTrue(director.restYieldRequested());
        UUID exploreIntentId = director.pendingIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();
        UUID restIntentId = director.runningIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();

        DiscretionaryAuthority.onRestYieldedForExplore(MOB, restIntentId, afterCommitment + 1L);
        assertEquals(exploreIntentId, director.pendingIntent().map(DiscretionaryIntent::intentId).orElseThrow());

        DiscretionaryAuthority.onExploreAdopted(MOB, afterCommitment + 2L);
        assertEquals(exploreIntentId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
    }

  // GAO-4-M4
    @Test
    void exploreIncumbentRestWinRequestsExploreYield() {
        seedOpinions(10f, 5f, 30f, 5f);
        neutralMood().seedChannels(0f, 5f, 0f, 85f, 0f);
        director.seedIncumbent(DiscretionaryActivity.EXPLORE, 25f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 5L;

        director.tick(tick(afterCommitment, idleObservation(), true));

        assertTrue(director.exploreYieldRequested());
        UUID restIntentId = director.pendingIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();
        UUID exploreIntentId = director.runningIntent()
                .map(DiscretionaryIntent::intentId)
                .orElseThrow();

        DiscretionaryAuthority.onExploreYieldedForRest(MOB, exploreIntentId, afterCommitment + 1L);
        assertEquals(restIntentId, director.pendingIntent().map(DiscretionaryIntent::intentId).orElseThrow());

        DiscretionaryAuthority.onRestAdopted(MOB, afterCommitment + 2L);
        assertEquals(restIntentId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
    }

  // GAO-4-M5
    @Test
    void combatInvalidatesPendingIntent() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        assertTrue(director.intent().isPresent());

        director.tick(tick(20L, idleObservation(), true, true));

        assertTrue(director.intent().isEmpty());
        assertTrue(hasTerminalDetail("COMBAT_TARGET"));
    }

  // GAO-4-M6
    @Test
    void mandatoryWorkInvalidatesIntent() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        assertTrue(director.intent().isPresent());

        ActivityObservationService.Observation mining = ActivityObservationService.summarize(
                List.of(ActivityClass.PROJECT_EXECUTION));
        director.tick(tick(20L, mining, true));

        assertTrue(director.intent().isEmpty());
    }

  // GAO-4-M8
    @Test
    void executorFailureMarksTerminalHonestly() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        DiscretionaryAuthority.onExploreFailedToStart(MOB, 15L);
        assertTrue(director.intent().isEmpty());
        assertTrue(hasTerminalDetail("no-route"));
    }

  // GAO-4-M10
    @Test
    void mandatoryMiningBlocksDiscretionaryScoring() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        ActivityObservationService.Observation mining = ActivityObservationService.summarize(
                List.of(ActivityClass.PROJECT_EXECUTION));

        director.tick(tick(10L, mining, true));

        assertTrue(director.intent().isEmpty());
        assertFalse(hasTraceStage(OpinionDecisionTrace.Stage.INTENT));
    }

  // GAO-4-M11
    @Test
    void tinyScoreChangesDoNotOscillateDuringCommitment() {
        seedOpinions(30f, 5f, 28f, 5f);
        neutralMood().seedChannels(0f, 40f, 0f, 40f, 5f);
        director.seedIncumbent(DiscretionaryActivity.EXPLORE, 31f, 0L);

        director.tick(tick(50L, idleObservation(), true));

        assertEquals(DiscretionaryActivity.EXPLORE, director.incumbentActivity().orElseThrow());
        assertFalse(director.restYieldRequested());
    }

    @Test
    void pendingIntentExpiresWithoutAdoption() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        UUID intentId = director.intent().get().intentId();

        director.tick(tick(10L + DiscretionaryDirectorConstants.PENDING_INTENT_TTL_TICKS + 1L,
                idleObservation(), false));

        assertTrue(director.intent().isEmpty());
        assertTrue(hasTerminalDetail("EXPIRED"));
    }

    @Test
    void traceRecordsIntentIdThroughLifecycle() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        UUID intentId = director.intent().get().intentId();

        DiscretionaryAuthority.onExploreAdopted(MOB, 12L);

        assertTrue(traceContainsIntent(intentId, OpinionDecisionTrace.Stage.ADOPT));
        assertTrue(traceContainsIntent(intentId, OpinionDecisionTrace.Stage.EXECUTOR));
    }

    @Test
    void opinionDisableInvalidatesIntentBeforeConsumerGatesRelax() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        DiscretionaryActivityDirector.tick(
                MOB, 10L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, true, BlockPos.ZERO);
        UUID staleId = director.intent().get().intentId();
        assertTrue(director.intent().isPresent());

        OpinionFeatureGate.testOverride = false;
        DiscretionaryActivityDirector.tick(
                MOB, 20L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, true, BlockPos.ZERO);

        assertTrue(director.intent().isEmpty());
        assertTrue(hasTerminalDetail("OPINION_DISABLED"));
        assertTrue(DiscretionaryAuthority.mayStartDiscretionaryExplore(MOB));

        OpinionFeatureGate.testOverride = true;
        DiscretionaryActivityDirector.tick(
                MOB, 30L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, true, BlockPos.ZERO);

        assertTrue(director.intent().isPresent());
        assertFalse(staleId.equals(director.intent().get().intentId()));
    }

    @Test
    void restDeliveryStopPreservesDirectorAuthorityWhileClaimLive() {
        seedOpinions(20f, 40f, 30f, 5f);
        neutralMood().seedChannels(0f, 5f, 0f, 80f, 0f);
        director.seedIncumbent(DiscretionaryActivity.REST, 30f, 100L);
        UUID restIntentId = director.runningIntent().get().intentId();

        director.markRestClaimOpened(150L);
        assertEquals(RestAuthorityPhase.CLAIMED, director.restAuthorityPhase());

        DiscretionaryAuthority.onRestDeliveryComplete(MOB, 200L);
        assertTrue(DiscretionaryAuthority.shouldPreserveRestIntentOnCampfireStop(MOB));
        assertTrue(director.runningIntent().isPresent());
        assertEquals(restIntentId, director.runningIntent().get().intentId());

        DiscretionaryAuthority.onRestClaimClosed(MOB, 250L, com.noobk.spmscavenger.experience.RestCloseReason.TIMEOUT);
        assertTrue(director.runningIntent().isEmpty());
        assertTrue(hasTerminalDetail("SUCCEEDED:rest-claim-closed:TIMEOUT"));
    }

    @Test
    void switchMarginUsesCurrentIncumbentUtilityNotAdoptionSnapshot() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.seedIncumbent(DiscretionaryActivity.EXPLORE, 70f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 5L;

        neutralMood().seedChannels(0f, 5f, 0f, 85f, 0f);
        director.tick(tick(afterCommitment, idleObservation(), true));

        assertTrue(director.exploreYieldRequested());
    }

    @Test
    void switchMarginBlocksSmallChallengerLead() {
        seedOpinions(30f, 5f, 28f, 5f);
        neutralMood().seedChannels(0f, 42f, 0f, 40f, 5f);
        director.seedIncumbent(DiscretionaryActivity.REST, 40f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 5L;

        director.tick(tick(afterCommitment, idleObservation(), true));

        assertFalse(director.restYieldRequested());
        assertTrue(director.pendingIntent().isEmpty());
    }

    @Test
    void adoptedAtTickZeroStillHonorsCommitment() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(0L, idleObservation(), true));
        UUID exploreId = director.intent().get().intentId();
        DiscretionaryAuthority.onExploreAdopted(MOB, 0L);

        neutralMood().seedChannels(0f, 5f, 0f, 85f, 0f);
        director.tick(tick(50L, idleObservation(), true));

        assertFalse(director.exploreYieldRequested());
        assertEquals(exploreId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
    }

    private DirectorTickInput tick(long gameTime, ActivityObservationService.Observation observation, boolean eligible) {
        return tick(gameTime, observation, eligible, false, true);
    }

    private DirectorTickInput tick(
            long gameTime,
            ActivityObservationService.Observation observation,
            boolean eligible,
            boolean combat) {
        return tick(gameTime, observation, eligible, combat, true);
    }

    private DirectorTickInput tick(
            long gameTime,
            ActivityObservationService.Observation observation,
            boolean eligible,
            boolean combat,
            boolean exploreAdoptionReady) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        return new DirectorTickInput(
                gameTime,
                true,
                false,
                combat,
                observation,
                DiscretionaryScoringInput.withoutPlace(
                        context.affectiveState(),
                        context.opinionMemory(),
                        DiscretionaryAvailability.bothPresent(),
                        eligible,
                        true),
                exploreAdoptionReady);
    }

    private static ActivityObservationService.Observation idleObservation() {
        return ActivityObservationService.summarize(
                EnumSet.of(ActivityClass.IDLE_CANDIDATE, ActivityClass.PASSIVE_COSMETIC));
    }

    private static AffectiveState neutralMood() {
        return OpinionExperienceRegistry.contextFor(MOB).affectiveState();
    }

    private void seedOpinions(
            float explorePref, float exploreRep, float restPref, float restRep) {
        OpinionMemory memory = OpinionExperienceRegistry.contextFor(MOB).opinionMemory();
        memory.seedActivity(ActivityKind.OVERLAND_EXPLORATION, explorePref, exploreRep, 0);
        memory.seedActivity(ActivityKind.REST, restPref, restRep, 0);
    }

    private boolean hasTraceStage(OpinionDecisionTrace.Stage stage) {
        return director.trace().snapshot().stream().anyMatch(e -> e.stage() == stage);
    }

    private boolean hasTerminalDetail(String fragment) {
        return director.trace().snapshot().stream()
                .anyMatch(e -> e.stage() == OpinionDecisionTrace.Stage.TERMINAL
                        && e.detail().contains(fragment));
    }

    private boolean traceContainsIntent(UUID intentId, OpinionDecisionTrace.Stage stage) {
        return director.trace().snapshot().stream()
                .anyMatch(e -> intentId.equals(e.intentId()) && e.stage() == stage);
    }
}
