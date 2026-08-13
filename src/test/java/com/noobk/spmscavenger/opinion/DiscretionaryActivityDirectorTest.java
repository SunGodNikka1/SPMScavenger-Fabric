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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        assertTrue(director.mustYield(DiscretionaryActivity.REST, director.lastGameTime()));
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

        assertTrue(director.mustYield(DiscretionaryActivity.REST, director.lastGameTime()));
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

        assertTrue(director.mustYield(DiscretionaryActivity.EXPLORE, director.lastGameTime()));
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
        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        assertEquals(
                OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY,
                decision.disposition());
        assertEquals(InvalidationCause.MANDATORY_AUTHORITY, decision.dispositionCause());
    }

    @Test
    void combatSuppressionPreservesItsExactCauseWithoutAnExistingIntent() {
        director.tick(tick(10L, idleObservation(), true, true));

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        assertEquals(
                OpinionDecisionTrace.DecisionDisposition.MANDATORY_AUTHORITY,
                decision.disposition());
        assertEquals(InvalidationCause.COMBAT_TARGET, decision.dispositionCause());
        assertTrue(decision.candidates().isEmpty());
        assertTrue(director.intent().isEmpty());
    }

  // GAO-4-M11
    @Test
    void tinyScoreChangesDoNotOscillateDuringCommitment() {
        seedOpinions(30f, 5f, 28f, 5f);
        neutralMood().seedChannels(0f, 40f, 0f, 40f, 5f);
        director.seedIncumbent(DiscretionaryActivity.EXPLORE, 31f, 0L);

        director.tick(tick(50L, idleObservation(), true));

        assertEquals(DiscretionaryActivity.EXPLORE, director.incumbentActivity().orElseThrow());
        assertFalse(director.mustYield(DiscretionaryActivity.REST, director.lastGameTime()));
        assertEquals(
                OpinionDecisionTrace.DecisionDisposition.COMMITMENT_HOLD,
                director.trace().snapshot().getLast().disposition());
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
    void oneDecisionCorrelatesFullScoresIntentExecutorAndTerminal() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);

        director.tick(tick(10L, idleObservation(), true));
        DiscretionaryIntent intent = director.pendingIntent().orElseThrow();
        OpinionDecisionTrace.Decision decision = decision(intent.decisionId());

        assertEquals(intent.intentId(), decision.intentId());
        assertEquals(2, decision.candidates().size());
        assertTrue(decision.candidates().stream().allMatch(candidate -> candidate.breakdown() != null));
        assertTrue(decision.transitions().stream().anyMatch(
                transition -> transition.stage() == OpinionDecisionTrace.Stage.SELECT
                        && transition.intentId() == null));
        assertTrue(decision.transitions().stream().anyMatch(
                transition -> transition.stage() == OpinionDecisionTrace.Stage.INTENT
                        && intent.intentId().equals(transition.intentId())));

        DiscretionaryAuthority.onExploreAdopted(MOB, 12L);
        DiscretionaryAuthority.onExploreTerminal(MOB, IntentLifecycle.SUCCEEDED, 20L, "complete");

        decision = decision(intent.decisionId());
        assertTrue(decision.closed());
        assertTrue(decision.transitions().stream().anyMatch(
                transition -> transition.stage() == OpinionDecisionTrace.Stage.ADOPT));
        assertTrue(decision.transitions().stream().anyMatch(
                transition -> transition.stage() == OpinionDecisionTrace.Stage.EXECUTOR));
        assertTrue(decision.transitions().stream().anyMatch(
                transition -> transition.stage() == OpinionDecisionTrace.Stage.TERMINAL
                        && transition.lifecycle() == IntentLifecycle.SUCCEEDED
                        && transition.invalidationCause() == InvalidationCause.NONE));
    }

    @Test
    void supersedingDecisionNeverInheritsPreviousIntentIdentity() {
        seedOpinions(10f, 5f, 55f, 4f);
        neutralMood().seedChannels(0f, 5f, 0f, 80f, 0f);
        director.tick(tick(10L, idleObservation(), true));
        DiscretionaryIntent oldIntent = director.pendingIntent().orElseThrow();

        seedOpinions(55f, 5f, 10f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(20L, idleObservation(), true));
        DiscretionaryIntent newIntent = director.pendingIntent().orElseThrow();

        assertNotEquals(oldIntent.intentId(), newIntent.intentId());
        assertNotEquals(oldIntent.decisionId(), newIntent.decisionId());
        OpinionDecisionTrace.Decision newDecision = decision(newIntent.decisionId());
        assertEquals(newIntent.intentId(), newDecision.intentId());
        assertFalse(newDecision.transitions().stream()
                .anyMatch(transition -> oldIntent.intentId().equals(transition.intentId())));
        assertTrue(decision(oldIntent.decisionId()).transitions().stream()
                .anyMatch(transition -> transition.stage() == OpinionDecisionTrace.Stage.TERMINAL
                        && transition.invalidationCause() == InvalidationCause.SUPERSEDED));
    }

    @Test
    void pendingIntentRetentionUsesPendingDispositionNotIncumbent() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        director.tick(tick(10L, idleObservation(), true));
        assertEquals(IntentLifecycle.PENDING, director.pendingIntent().orElseThrow().lifecycle());

        director.tick(tick(20L, idleObservation(), true));

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        assertEquals(
                OpinionDecisionTrace.DecisionDisposition.PENDING_INTENT_RETAINED,
                decision.disposition());
        assertTrue(director.incumbentActivity().isEmpty());
    }

    @Test
    void restAdoptionGateRecordsSuppressionWithoutDiscardingItsScore() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);

        director.tick(tick(
                10L,
                idleObservation(),
                true,
                false,
                ActivityAdmissions.of(
                        ActivityAdmission.blocked(
                                true, ActivityAdoptionBlocker.EXPLORE_NOT_READY, "test"),
                        ActivityAdmission.blocked(
                                true, ActivityAdoptionBlocker.NO_CAMPFIRE_ITEM, "test-not-ready"))));

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        OpinionDecisionTrace.Candidate rest = decision.candidates().stream()
                .filter(candidate -> candidate.activity() == DiscretionaryActivity.REST)
                .findFirst()
                .orElseThrow();
        assertEquals(OpinionDecisionTrace.CandidateState.SUPPRESSED, rest.state());
        assertEquals(
                OpinionDecisionTrace.SuppressionReason.ADOPTION_NOT_READY,
                rest.suppressionReason());
        assertTrue(rest.suppressionDetail().contains("NO_CAMPFIRE_ITEM"));
        assertTrue(director.intent().isEmpty());
    }

    @Test
    void exploreAdoptionGateRecordsSuppressionWithoutDiscardingItsScore() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);

        director.tick(tick(10L, idleObservation(), true, false, false));

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        OpinionDecisionTrace.Candidate explore = decision.candidates().stream()
                .filter(candidate -> candidate.activity() == DiscretionaryActivity.EXPLORE)
                .findFirst()
                .orElseThrow();
        assertEquals(OpinionDecisionTrace.CandidateState.SUPPRESSED, explore.state());
        assertEquals(
                OpinionDecisionTrace.SuppressionReason.ADOPTION_NOT_READY,
                explore.suppressionReason());
        assertTrue(explore.breakdown().total() > 0f);
        assertEquals(DiscretionaryActivity.REST, decision.selectedActivity());
    }

    @Test
    void noExecutorDecisionIsStructuredAndDoesNotIssueIntent() {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        DirectorTickInput input = new DirectorTickInput(
                10L,
                true,
                false,
                false,
                idleObservation(),
                new DiscretionaryScoringInput(
                        context.affectiveState(),
                        context.opinionMemory(),
                        DiscretionaryAvailability.none(),
                        true,
                        true),
                TestActivityAdmissions.bothReady());

        director.tick(input);

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().getLast();
        assertEquals(OpinionDecisionTrace.DecisionDisposition.NO_CANDIDATES, decision.disposition());
        assertTrue(decision.candidates().stream().allMatch(candidate ->
                candidate.suppressionReason()
                        == OpinionDecisionTrace.SuppressionReason.EXECUTOR_UNAVAILABLE));
        assertTrue(director.intent().isEmpty());
    }

    @Test
    void opinionDisableInvalidatesIntentBeforeConsumerGatesRelax() {
        seedOpinions(55f, 5f, 11f, 4f);
        neutralMood().seedChannels(0f, 80f, 0f, 5f, 10f);
        DiscretionaryActivityDirector.tick(
                MOB, 10L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());
        UUID staleId = director.intent().get().intentId();
        assertTrue(director.intent().isPresent());

        OpinionFeatureGate.testOverride = false;
        DiscretionaryActivityDirector.tick(
                MOB, 20L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());

        assertTrue(director.intent().isEmpty());
        assertTrue(hasTerminalDetail("OPINION_DISABLED"));
        assertTrue(DiscretionaryAuthority.mayStartDiscretionaryExplore(MOB));

        OpinionFeatureGate.testOverride = true;
        DiscretionaryActivityDirector.tick(
                MOB, 30L, idleObservation(), DiscretionaryAvailability.bothPresent(), false, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());

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

        assertTrue(director.mustYield(DiscretionaryActivity.EXPLORE, director.lastGameTime()));
    }

    @Test
    void switchMarginBlocksSmallChallengerLead() {
        seedOpinions(38f, 5f, 28f, 5f);
        neutralMood().seedChannels(0f, 42f, 0f, 40f, 5f);
        director.seedIncumbent(DiscretionaryActivity.REST, 40f, 0L);
        long afterCommitment = DiscretionaryDirectorConstants.MIN_COMMITMENT_TICKS + 5L;

        director.tick(tick(afterCommitment, idleObservation(), true));

        assertFalse(director.mustYield(DiscretionaryActivity.REST, director.lastGameTime()));
        assertTrue(director.pendingIntent().isEmpty());
        assertEquals(
                OpinionDecisionTrace.DecisionDisposition.SWITCH_MARGIN_HOLD,
                director.trace().snapshot().getLast().disposition());
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

        assertFalse(director.mustYield(DiscretionaryActivity.EXPLORE, director.lastGameTime()));
        assertEquals(exploreId, director.runningIntent().map(DiscretionaryIntent::intentId).orElseThrow());
    }

    private DirectorTickInput tick(long gameTime, ActivityObservationService.Observation observation, boolean eligible) {
        return tick(gameTime, observation, eligible, false, TestActivityAdmissions.bothReady());
    }

    private DirectorTickInput tick(
            long gameTime,
            ActivityObservationService.Observation observation,
            boolean eligible,
            boolean combat) {
        return tick(gameTime, observation, eligible, combat, TestActivityAdmissions.bothReady());
    }

    private DirectorTickInput tick(
            long gameTime,
            ActivityObservationService.Observation observation,
            boolean eligible,
            boolean combat,
            ActivityAdmissions admissions) {
        MobExperienceContext context = OpinionExperienceRegistry.contextFor(MOB);
        return new DirectorTickInput(
                gameTime,
                true,
                false,
                combat,
                observation,
                new DiscretionaryScoringInput(
                        context.affectiveState(),
                        context.opinionMemory(),
                        DiscretionaryAvailability.bothPresent(),
                        eligible,
                        true),
                admissions);
    }

    private DirectorTickInput tick(
            long gameTime,
            ActivityObservationService.Observation observation,
            boolean eligible,
            boolean combat,
            boolean exploreReady) {
        return tick(gameTime, observation, eligible, combat, TestActivityAdmissions.exploreReady(exploreReady));
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
        return director.trace().snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> transition.stage() == stage);
    }

    private boolean hasTerminalDetail(String fragment) {
        return director.trace().snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> transition.stage() == OpinionDecisionTrace.Stage.TERMINAL
                        && (transition.lifecycle() + ":" + transition.detail()).contains(fragment));
    }

    private boolean traceContainsIntent(UUID intentId, OpinionDecisionTrace.Stage stage) {
        return director.trace().snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> intentId.equals(transition.intentId())
                        && transition.stage() == stage);
    }

    private OpinionDecisionTrace.Decision decision(long decisionId) {
        return director.trace().snapshot().stream()
                .filter(decision -> decision.decisionId() == decisionId)
                .findFirst()
                .orElseThrow();
    }
}
