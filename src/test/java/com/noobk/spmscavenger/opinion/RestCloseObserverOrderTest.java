package com.noobk.spmscavenger.opinion;

import com.noobk.spmscavenger.activity.ActivityClass;
import com.noobk.spmscavenger.activity.ActivityObservationService;
import com.noobk.spmscavenger.experience.ActivityEpisode;
import com.noobk.spmscavenger.experience.ActivityKind;
import com.noobk.spmscavenger.experience.ExperienceEmitters;
import com.noobk.spmscavenger.experience.OpinionExperienceRegistry;
import com.noobk.spmscavenger.experience.RestAnchorType;
import com.noobk.spmscavenger.experience.RestCloseReason;
import com.noobk.spmscavenger.experience.RestSessionClaim;
import com.noobk.spmscavenger.experience.RestSourceKind;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestCloseObserverOrderTest {

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

    @Test
    void combatClaimCloseMatchesCoordinatorObserverOrder() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();

        closeLikeCoordinator(claim, RestCloseReason.COMBAT, 200L);

        assertFalse(hasRunningRest());
        assertTrue(terminalDetailContains("INVALIDATED:rest-claim-closed:COMBAT"));

        DiscretionaryActivityDirector.tick(
                MOB, 210L, idleObservation(), DiscretionaryAvailability.bothPresent(), true, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());
        assertFalse(hasRunningRest());
        assertEquals(0.0f, restPreference());
    }

    @Test
    void mandatoryWorkClaimCloseMatchesCoordinatorObserverOrder() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();

        closeLikeCoordinator(claim, RestCloseReason.MANDATORY_WORK, 200L);

        assertFalse(hasRunningRest());
        assertTrue(terminalDetailContains("INVALIDATED:rest-claim-closed:MANDATORY_WORK"));

        ActivityObservationService.Observation mining = ActivityObservationService.summarize(
                List.of(ActivityClass.PROJECT_EXECUTION));
        DiscretionaryActivityDirector.tick(
                MOB, 210L, mining, DiscretionaryAvailability.bothPresent(), false, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());
        assertFalse(hasRunningRest());
    }

    @Test
    void playerOrderClaimCloseMatchesCoordinatorObserverOrder() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();

        closeLikeCoordinator(claim, RestCloseReason.PLAYER_ORDER, 200L);

        assertFalse(hasRunningRest());
        assertTrue(terminalDetailContains("INVALIDATED:rest-claim-closed:PLAYER_ORDER"));

        ActivityObservationService.Observation commanded = ActivityObservationService.summarize(
                List.of(ActivityClass.MANDATORY_COMMAND));
        DiscretionaryActivityDirector.tick(
                MOB, 210L, commanded, DiscretionaryAvailability.bothPresent(), false, TestActivityAdmissions.bothReady(),
                ActivityContinuations.none());
        assertFalse(hasRunningRest());
    }

    @Test
    void restOpenAloneIsFalseGreenGuardForTimeoutSuccess() {
        RestSessionClaim claim = liveClaim();
        ExperienceEmitters.restSessionOpened(MOB, claim, 10_000L);

        assertEquals(0.0f, restPreference());
        assertFalse(episodeClosed(claim.claimId()));
    }

    @Test
    void timeoutClaimCloseRecordsSuccessAndPositivePreference() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());

        closeLikeCoordinator(claim, RestCloseReason.TIMEOUT, 200L);

        assertFalse(hasRunningRest());
        assertTrue(terminalDetailContains("SUCCEEDED:rest-claim-closed:TIMEOUT"));
        assertTrue(restPreference() > 0.0f);
    }

    @Test
    void restLearningReceiptAttachesToTheOriginatingDecision() {
        seedRunningRest();
        DiscretionaryIntent intent = director.runningIntent().orElseThrow();
        RestSessionClaim claim = liveClaim(Optional.of(intent.intentId()));
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());

        closeLikeCoordinator(claim, RestCloseReason.TIMEOUT, 200L);

        OpinionDecisionTrace.Decision decision = director.trace().snapshot().stream()
                .filter(candidate -> candidate.decisionId() == intent.decisionId())
                .findFirst()
                .orElseThrow();
        assertEquals(1, decision.learningOutcomes().size());
        OpinionDecisionTrace.LearningOutcome learning = decision.learningOutcomes().getFirst();
        assertEquals(ActivityKind.REST, learning.activity());
        assertTrue(learning.activityLearningEligible());
        assertTrue(learning.activityDelta().preference() > 0f);
        assertTrue(learning.activityDelta().changedAnything());
        assertTrue(learning.placePreferenceDeltas().isEmpty());
        assertTrue(learning.environmentPreferenceDeltas().isEmpty());
    }

    @Test
    void protectedRestInterruptionRecordsNoInventedLearning() {
        seedRunningRest();
        DiscretionaryIntent intent = director.runningIntent().orElseThrow();
        RestSessionClaim claim = liveClaim(Optional.of(intent.intentId()));
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());

        closeLikeCoordinator(claim, RestCloseReason.COMBAT, 200L);

        OpinionDecisionTrace.LearningOutcome learning = director.trace().snapshot().stream()
                .filter(candidate -> candidate.decisionId() == intent.decisionId())
                .findFirst()
                .orElseThrow()
                .learningOutcomes()
                .getFirst();
        assertFalse(learning.activityLearningEligible());
        assertFalse(learning.changedAnything());
    }

    @Test
    void combatCloseDoesNotTeachRestDislike() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());
        float beforeClose = restPreference();

        closeLikeCoordinator(claim, RestCloseReason.COMBAT, 200L);

        assertTrue(terminalDetailContains("INVALIDATED:rest-claim-closed:COMBAT"));
        assertEquals(beforeClose, restPreference(), 0.0001f);
    }

    @Test
    void fireInvalidCloseDoesNotTeachRestDislike() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());
        float beforeClose = restPreference();

        closeLikeCoordinator(claim, RestCloseReason.FIRE_INVALID, 200L);

        assertTrue(terminalDetailContains("INTERRUPTED:rest-claim-closed:FIRE_INVALID"));
        assertEquals(beforeClose, restPreference(), 0.0001f);
    }

    @Test
    void chunkUnloadCloseDoesNotTeachRestDislike() {
        seedRunningRest();
        RestSessionClaim claim = liveClaim();
        ExperienceEmitters.restSessionOpened(MOB, claim, claim.arrivedAt());
        float beforeClose = restPreference();

        closeLikeCoordinator(claim, RestCloseReason.CHUNK_UNLOAD, 200L);

        assertTrue(terminalDetailContains("INVALIDATED:rest-claim-closed:CHUNK_UNLOAD"));
        assertEquals(beforeClose, restPreference(), 0.0001f);
    }

    private void seedRunningRest() {
        director.seedIncumbent(DiscretionaryActivity.REST, 30f, 100L);
        director.markRestClaimOpened(150L);
    }

    private static RestSessionClaim liveClaim() {
        return liveClaim(Optional.empty());
    }

    private static RestSessionClaim liveClaim(Optional<UUID> sourceIntentId) {
        UUID claimId = UUID.randomUUID();
        return new RestSessionClaim(
                claimId,
                sourceIntentId,
                claimId,
                RestSourceKind.DISCRETIONARY_REST,
                new BlockPos(1, 64, 2),
                RestAnchorType.CAMPFIRE,
                150L,
                150L,
                150L,
                Optional.empty());
    }

    private static void closeLikeCoordinator(RestSessionClaim claim, RestCloseReason reason, long gameTime) {
        RestSessionClaim closed = claim.closed(reason, gameTime);
        OpinionExperienceRegistry.contextFor(MOB).setRestClaim(Optional.of(closed));
        ExperienceEmitters.restSessionClosed(MOB, closed, reason, gameTime);
        DiscretionaryAuthority.onRestClaimClosed(MOB, gameTime, reason);
    }

    private boolean hasRunningRest() {
        return director.runningIntent()
                .filter(intent -> intent.activity() == DiscretionaryActivity.REST)
                .isPresent();
    }

    private boolean terminalDetailContains(String fragment) {
        return director.trace().snapshot().stream()
                .flatMap(decision -> decision.transitions().stream())
                .anyMatch(transition -> transition.stage() == OpinionDecisionTrace.Stage.TERMINAL
                        && (transition.lifecycle() + ":" + transition.detail()).contains(fragment));
    }

    private static float restPreference() {
        return OpinionExperienceRegistry.contextFor(MOB).opinionMemory().preference(ActivityKind.REST);
    }

    private boolean episodeClosed(UUID claimId) {
        return OpinionExperienceRegistry.contextFor(MOB)
                .findEpisode(claimId)
                .map(ActivityEpisode::isClosed)
                .orElse(false);
    }

    private static ActivityObservationService.Observation idleObservation() {
        return ActivityObservationService.summarize(
                EnumSet.of(ActivityClass.IDLE_CANDIDATE, ActivityClass.PASSIVE_COSMETIC));
    }
}
